package com.gymsystem.booking.waitlist;

import com.gymsystem.booking.Booking;
import com.gymsystem.booking.BookingRepository;
import com.gymsystem.booking.ClassSession;
import com.gymsystem.booking.ClassSessionRepository;
import com.gymsystem.booking.ClassType;
import com.gymsystem.booking.config.BookingConfig;
import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.notifications.EmailService;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Fila de espera de aulas lotadas.
 *
 * A regra que sustenta o recurso: quando alguem cancela, o proximo da fila e
 * promovido e ganha um prazo para confirmar; vencido o prazo, a vaga passa
 * adiante. Se a promocao nao acontecer, ou nao expirar, a vaga fica presa com
 * alguem que nao vai usa-la e a aula sai vazia.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaitlistServiceTest {

    private static final Long   TENANT_ID = 2L;
    private static final Long   SESSION_ID = 10L;
    private static final String EMAIL = "aluno@local.test";

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private ClassSessionRepository sessionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BookingConfigService configService;
    @Mock private EmailService emailService;

    @InjectMocks private WaitlistService service;

    private Tenant tenant;
    private User aluno;
    private ClassSession sessao;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Fit").active(true).build();
        aluno  = User.builder().id(1L).email(EMAIL).role(UserRole.USER).active(true).build();

        var tipo = ClassType.builder().id(3L).code("PILATES").name("Pilates").active(true).tenant(tenant).build();
        Instant inicio = Instant.now().plus(24, ChronoUnit.HOURS);
        sessao = ClassSession.builder()
                .id(SESSION_ID).classType(tipo).tenant(tenant)
                .startAt(inicio).endAt(inicio.plus(1, ChronoUnit.HOURS))
                .capacity(5).canceled(false)
                .createdByAdminId(1L).createdAt(Instant.now())
                .build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sessao));
        when(configService.get()).thenReturn(config(true, 2));
        when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

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
    @DisplayName("Entrar na fila")
    class Entrar {

        @Test
        @DisplayName("aula lotada: entra na fila na próxima posição")
        void entraNaFilaQuandoLotada() {
            aulaLotada();
            when(waitlistRepository.findMaxPosition(SESSION_ID)).thenReturn(2);

            var resposta = service.join(SESSION_ID);

            assertThat(resposta.position()).isEqualTo(3);
            assertThat(resposta.status()).isEqualTo(WaitlistStatus.WAITING.name());
        }

        @Test
        @DisplayName("ainda tem vaga: manda reservar direto em vez de entrar na fila")
        void recusaQuandoAindaTemVaga() {
            when(bookingRepository.countActiveBySessionId(SESSION_ID, TENANT_ID)).thenReturn(3L); // cap. 5

            assertThatThrownBy(() -> service.join(SESSION_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Book directly");
        }

        @Test
        @DisplayName("fila desligada na configuração da academia: não aceita")
        void recusaQuandoFilaDesligada() {
            when(configService.get()).thenReturn(config(false, 2));

            assertThatThrownBy(() -> service.join(SESSION_ID))
                    .hasMessageContaining("not enabled");
        }

        @Test
        @DisplayName("aula de outra academia é bloqueada mesmo com o id certo")
        void bloqueiaSessaoDeOutraAcademia() {
            sessao.setTenant(Tenant.builder().id(99L).slug("outra").name("Outra").active(true).build());

            assertThatThrownBy(() -> service.join(SESSION_ID))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("aula cancelada não tem fila")
        void recusaAulaCancelada() {
            aulaLotada();
            sessao.setCanceled(true);

            assertThatThrownBy(() -> service.join(SESSION_ID))
                    .hasMessageContaining("canceled");
        }

        @Test
        @DisplayName("não entra duas vezes na mesma fila")
        void recusaEntradaDuplicada() {
            aulaLotada();
            when(waitlistRepository.existsBySessionIdAndUserIdAndStatusIn(anyLong(), anyLong(), any()))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.join(SESSION_ID))
                    .hasMessageContaining("already in the waitlist");
        }

        @Test
        @DisplayName("quem já tem reserva ativa não entra na fila da mesma aula")
        void recusaQuemJaTemReserva() {
            aulaLotada();
            when(bookingRepository.findActiveBySessionIdAndUserId(SESSION_ID, aluno.getId(), TENANT_ID))
                    .thenReturn(Optional.of(new Booking()));

            assertThatThrownBy(() -> service.join(SESSION_ID))
                    .hasMessageContaining("already have an active booking");
        }
    }

    @Nested
    @DisplayName("Promoção")
    class Promocao {

        @Test
        @DisplayName("promove o próximo da fila, marca o prazo e avisa por e-mail")
        void promoveProximo() {
            var entrada = entrada(WaitlistStatus.WAITING, 1);
            when(waitlistRepository.findNextWaiting(SESSION_ID, TENANT_ID)).thenReturn(Optional.of(entrada));

            service.promoteNext(SESSION_ID, TENANT_ID);

            assertThat(entrada.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
            assertThat(entrada.getNotifiedAt()).isNotNull();
            assertThat(entrada.getExpiresAt()).isAfter(Instant.now());
            verify(emailService).sendWaitlistPromotion(eq(aluno), eq(sessao), any());
        }

        @Test
        @DisplayName("o prazo de confirmação sai da configuração da academia")
        void prazoVemDaConfiguracao() {
            when(configService.get()).thenReturn(config(true, 6)); // 6 horas
            var entrada = entrada(WaitlistStatus.WAITING, 1);
            when(waitlistRepository.findNextWaiting(SESSION_ID, TENANT_ID)).thenReturn(Optional.of(entrada));

            service.promoteNext(SESSION_ID, TENANT_ID);

            long horas = ChronoUnit.HOURS.between(Instant.now(), entrada.getExpiresAt());
            assertThat(horas).isBetween(5L, 6L);
        }

        @Test
        @DisplayName("fila vazia: não promove ninguém nem manda e-mail")
        void filaVazia() {
            when(waitlistRepository.findNextWaiting(SESSION_ID, TENANT_ID)).thenReturn(Optional.empty());

            service.promoteNext(SESSION_ID, TENANT_ID);

            verify(emailService, never()).sendWaitlistPromotion(any(), any(), any());
            verify(waitlistRepository, never()).save(any());
        }

        @Test
        @DisplayName("com a fila desligada, cancelar não promove ninguém")
        void naoPromoveComFilaDesligada() {
            when(configService.get()).thenReturn(config(false, 2));

            service.promoteNext(SESSION_ID, TENANT_ID);

            verify(waitlistRepository, never()).findNextWaiting(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("Expiração das promoções")
    class Expiracao {

        @Test
        @DisplayName("promoção vencida expira, avisa o aluno e passa a vaga adiante")
        void expiraEPassaAdiante() {
            var vencida = entrada(WaitlistStatus.PROMOTED, 1);
            vencida.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(waitlistRepository.findExpiredPromotions(any())).thenReturn(List.of(vencida));
            when(waitlistRepository.findNextWaiting(SESSION_ID, TENANT_ID))
                    .thenReturn(Optional.of(entrada(WaitlistStatus.WAITING, 2)));

            service.expireStalePromotions();

            assertThat(vencida.getStatus()).isEqualTo(WaitlistStatus.EXPIRED);
            verify(emailService).sendWaitlistExpired(aluno, sessao);
            // A vaga nao pode ficar presa: o proximo tem que ser promovido.
            verify(waitlistRepository).findNextWaiting(SESSION_ID, TENANT_ID);
        }

        @Test
        @DisplayName("nada vencido: o job não faz nada")
        void nadaVencido() {
            when(waitlistRepository.findExpiredPromotions(any())).thenReturn(List.of());

            service.expireStalePromotions();

            verify(waitlistRepository, never()).save(any());
            verify(emailService, never()).sendWaitlistExpired(any(), any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private void aulaLotada() {
        when(bookingRepository.countActiveBySessionId(SESSION_ID, TENANT_ID)).thenReturn(5L); // capacidade 5
    }

    private WaitlistEntry entrada(WaitlistStatus status, int posicao) {
        return WaitlistEntry.builder()
                .id(100L).tenant(tenant).session(sessao).user(aluno)
                .position(posicao).status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    private BookingConfig config(boolean waitlistEnabled, int promotionHours) {
        return BookingConfig.builder()
                .id(1L).tenant(tenant)
                .publishDaysBeforeMonth(15).businessDays("MON-SAT")
                .cancelCutoffHours(0).onePerDayPerType(true)
                .waitlistEnabled(waitlistEnabled)
                .waitlistPromotionHours(promotionHours)
                .updatedAt(Instant.now())
                .build();
    }
}
