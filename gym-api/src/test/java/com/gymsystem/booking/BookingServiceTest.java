package com.gymsystem.booking;

import com.gymsystem.booking.config.BookingConfig;
import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.booking.waitlist.WaitlistService;
import com.gymsystem.common.ratelimit.RateLimiter;
import com.gymsystem.i18n.I18n;
import com.gymsystem.payments.Subscription;
import com.gymsystem.payments.SubscriptionRepository;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regras de reserva: capacidade, duplicidade, janela de cancelamento e
 * isolamento entre academias.
 *
 * Sao as regras que decidem se um aluno entra ou nao numa aula, e nenhuma delas
 * tinha teste. Os horarios sao sempre relativos ao agora, para os casos nao
 * comecarem a falhar com a passagem do tempo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    private static final Long   TENANT_ID = 2L;
    private static final Long   OUTRO_TENANT = 99L;
    private static final Long   SESSION_ID = 10L;
    private static final String EMAIL = "aluno@local.test";

    @Mock private ClassTypeRepository classTypeRepository;
    @Mock private ClassSessionRepository classSessionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingPolicyRepository policyRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private BookingConfigService bookingConfigService;
    @Mock private TenantRepository tenantRepository;
    @Mock private I18n i18n;
    @Mock private WaitlistService waitlistService;

    // Real: a regra de intervalo minimo faz parte do comportamento sob teste.
    private final RateLimiter rateLimiter = new RateLimiter();

    private BookingService service;

    private Tenant tenant;
    private User aluno;
    private ClassType tipoAula;

    @BeforeEach
    void setUp() {
        service = new BookingService(
                classTypeRepository, classSessionRepository, bookingRepository, userRepository,
                policyRepository, subscriptionRepository, bookingConfigService, tenantRepository,
                i18n, rateLimiter, waitlistService);
        ReflectionTestUtils.setField(service, "bookMinIntervalMs", 0L);
        ReflectionTestUtils.setField(service, "cancelMinIntervalMs", 0L);

        tenant   = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Academia Fit").active(true).build();
        aluno    = User.builder().id(1L).email(EMAIL).role(UserRole.USER).active(true).build();
        tipoAula = ClassType.builder().id(3L).code("PILATES").name("Pilates").active(true).tenant(tenant).build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        // Todas as chamadas em producao sao msg("codigo") sem varargs; devolver o
        // proprio codigo deixa a asserção legivel sem depender do messages.properties.
        when(i18n.msg(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(bookingConfigService.get()).thenReturn(config(true, 0));
        when(policyRepository.findTopByTenantIdOrderByIdAsc(TENANT_ID)).thenReturn(Optional.empty());
        comAssinaturaAtiva();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId(500L);
            return b;
        });

        TenantContext.set(TENANT_ID, "academia-fit");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Reservar")
    class Reservar {

        @Test
        @DisplayName("caminho feliz: cria a reserva como BOOKED na academia do contexto")
        void reservaComSucesso() {
            comSessao(sessao(20, 5));
            when(bookingRepository.countActiveBySessionId(SESSION_ID, TENANT_ID)).thenReturn(2L);

            var response = service.bookSession(SESSION_ID);

            assertThat(response.status()).isEqualTo("BOOKED");

            var captor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(captor.capture());
            assertThat(captor.getValue().getTenant().getId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().getUser().getId()).isEqualTo(aluno.getId());
        }

        @Test
        @DisplayName("aula lotada é recusada")
        void recusaAulaLotada() {
            comSessao(sessao(20, 5));
            when(bookingRepository.countActiveBySessionId(SESSION_ID, TENANT_ID)).thenReturn(5L);

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("booking.full");
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("capacidade estourada (dado inconsistente) também é recusada, não só o empate")
        void recusaQuandoJaPassouDaCapacidade() {
            comSessao(sessao(20, 5));
            when(bookingRepository.countActiveBySessionId(SESSION_ID, TENANT_ID)).thenReturn(7L);

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .hasMessageContaining("booking.full");
        }

        @Test
        @DisplayName("o mesmo aluno não reserva duas vezes a mesma aula")
        void recusaReservaDuplicada() {
            comSessao(sessao(20, 5));
            when(bookingRepository.findActiveBySessionIdAndUserId(SESSION_ID, aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.of(new Booking()));

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .hasMessageContaining("booking.duplicate.session");
        }

        @Test
        @DisplayName("uma aula por tipo por dia, quando a regra está ligada")
        void recusaSegundaAulaDoMesmoTipoNoDia() {
            comSessao(sessao(20, 5));
            when(bookingRepository.countActiveForUserByTypeAndDay(
                    eq(aluno.getId()), eq(TENANT_ID), eq(tipoAula.getId()), any(), any()))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .hasMessageContaining("booking.oneperday");
        }

        @Test
        @DisplayName("com a regra desligada, duas aulas do mesmo tipo no dia são permitidas")
        void permiteSegundaAulaQuandoRegraDesligada() {
            when(bookingConfigService.get()).thenReturn(config(false, 0));
            comSessao(sessao(20, 5));
            when(bookingRepository.countActiveForUserByTypeAndDay(anyLong(), anyLong(), anyLong(), any(), any()))
                    .thenReturn(1L);

            assertThat(service.bookSession(SESSION_ID).status()).isEqualTo("BOOKED");
        }

        @Test
        @DisplayName("aula cancelada não aceita reserva")
        void recusaAulaCancelada() {
            var s = sessao(20, 5);
            s.setCanceled(true);
            comSessao(s);

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .hasMessageContaining("booking.session.canceled");
        }

        @Test
        @DisplayName("aula que já começou não aceita reserva")
        void recusaAulaJaIniciada() {
            comSessao(sessao(-1, 5));

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .hasMessageContaining("booking.already.started");
        }

        @Test
        @DisplayName("aluno sem assinatura ativa não reserva")
        void recusaSemAssinatura() {
            when(subscriptionRepository.findByUserIdAndTenantIdAndStatusIn(anyLong(), anyLong(), any()))
                    .thenReturn(Optional.empty());
            comSessao(sessao(20, 5));

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .hasMessageContaining("active subscription");
        }

        @Test
        @DisplayName("aula de outra academia é bloqueada mesmo com o id certo")
        void bloqueiaSessaoDeOutraAcademia() {
            var outra = Tenant.builder().id(OUTRO_TENANT).slug("outra").name("Outra").active(true).build();
            var s = sessao(20, 5);
            s.setTenant(outra);
            comSessao(s);

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .isInstanceOf(SecurityException.class);
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("aula inexistente dá erro de argumento")
        void recusaSessaoInexistente() {
            when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cliques repetidos em sequência são barrados pelo intervalo mínimo")
        void barraCliqueDuplo() {
            ReflectionTestUtils.setField(service, "bookMinIntervalMs", 5_000L);
            comSessao(sessao(20, 5));

            service.bookSession(SESSION_ID);

            assertThatThrownBy(() -> service.bookSession(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Too many booking attempts");
        }
    }

    @Nested
    @DisplayName("Cancelar")
    class Cancelar {

        @Test
        @DisplayName("cancela e chama a promoção da fila de espera")
        void cancelaEPromoveDaFila() {
            var booking = reservaDe(sessao(48, 5));
            when(bookingRepository.findByIdAndUserId(booking.getId(), aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.of(booking));

            service.cancelMyBooking(booking.getId());

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELED);
            assertThat(booking.getCanceledAt()).isNotNull();
            verify(waitlistService).promoteNext(SESSION_ID, TENANT_ID);
        }

        @Test
        @DisplayName("dentro da janela de corte, o cancelamento é recusado")
        void recusaDentroDoCorte() {
            when(bookingConfigService.get()).thenReturn(config(true, 12)); // corte de 12h
            var booking = reservaDe(sessao(3, 5));                          // aula em 3h
            when(bookingRepository.findByIdAndUserId(booking.getId(), aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> service.cancelMyBooking(booking.getId()))
                    .hasMessageContaining("booking.cancel.cutoff");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.BOOKED);
            verify(waitlistService, never()).promoteNext(anyLong(), anyLong());
        }

        @Test
        @DisplayName("fora da janela de corte, cancela normalmente")
        void permiteForaDoCorte() {
            when(bookingConfigService.get()).thenReturn(config(true, 12)); // corte de 12h
            var booking = reservaDe(sessao(48, 5));                         // aula em 48h
            when(bookingRepository.findByIdAndUserId(booking.getId(), aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.of(booking));

            service.cancelMyBooking(booking.getId());

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELED);
        }

        @Test
        @DisplayName("cancelar de novo é inofensivo e não promove ninguém duas vezes")
        void cancelarDuasVezesEIdempotente() {
            var booking = reservaDe(sessao(48, 5));
            booking.setStatus(BookingStatus.CANCELED);
            when(bookingRepository.findByIdAndUserId(booking.getId(), aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.of(booking));

            service.cancelMyBooking(booking.getId());

            verify(waitlistService, never()).promoteNext(anyLong(), anyLong());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("a busca da reserva é escopada por usuário e academia")
        void buscaEscopada() {
            when(bookingRepository.findByIdAndUserId(77L, aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelMyBooking(77L))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(bookingRepository).findByIdAndUserId(77L, aluno.getId(), TENANT_ID);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Sessão começando daqui a `horas` (negativo = já começou), com a capacidade dada. */
    private ClassSession sessao(int horas, int capacidade) {
        Instant inicio = Instant.now().plus(horas, ChronoUnit.HOURS);
        return ClassSession.builder()
                .id(SESSION_ID)
                .classType(tipoAula)
                .tenant(tenant)
                .startAt(inicio)
                .endAt(inicio.plus(1, ChronoUnit.HOURS))
                .capacity(capacidade)
                .canceled(false)
                .createdByAdminId(1L)
                .createdAt(Instant.now())
                .build();
    }

    private void comSessao(ClassSession s) {
        when(classSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
    }

    private Booking reservaDe(ClassSession s) {
        return Booking.builder()
                .id(500L).session(s).user(aluno).tenant(tenant)
                .status(BookingStatus.BOOKED).createdAt(Instant.now())
                .build();
    }

    /** publishDaysBeforeMonth alto para a janela mensal nunca ser o motivo da recusa. */
    private BookingConfig config(boolean onePerDayPerType, int cancelCutoffHours) {
        return BookingConfig.builder()
                .id(1L).tenant(tenant)
                .publishDaysBeforeMonth(3650)
                .businessDays("MON-SAT")
                .cancelCutoffHours(cancelCutoffHours)
                .onePerDayPerType(onePerDayPerType)
                .waitlistEnabled(true)
                .waitlistPromotionHours(2)
                .updatedAt(Instant.now())
                .build();
    }

    private void comAssinaturaAtiva() {
        when(subscriptionRepository.findByUserIdAndTenantIdAndStatusIn(anyLong(), anyLong(), any()))
                .thenReturn(Optional.of(new Subscription()));
    }
}
