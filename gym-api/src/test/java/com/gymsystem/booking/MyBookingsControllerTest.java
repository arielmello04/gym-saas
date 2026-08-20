package com.gymsystem.booking;

import com.gymsystem.booking.config.BookingConfig;
import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.*;

/**
 * Guarda de regressao do endpoint /api/v1/my/bookings.
 *
 * Ele chamava a sobrecarga do repositorio SEM tenantId, entao um aluno que
 * frequenta duas academias via as reservas das duas misturadas na mesma lista.
 * O teste de escopo abaixo existe exatamente para isso nao voltar - as duas
 * sobrecargas conviviam lado a lado no repositorio e era facil pegar a errada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyBookingsControllerTest {

    private static final Long   TENANT_ID = 2L;
    private static final String EMAIL = "aluno@local.test";

    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingConfigService bookingConfigService;

    @InjectMocks private MyBookingsController controller;

    private User aluno;
    private Tenant tenant;
    private ClassType tipoAula;

    @BeforeEach
    void setUp() {
        tenant   = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Academia Fit").active(true).build();
        aluno    = User.builder().id(1L).email(EMAIL).role(UserRole.USER).active(true).build();
        tipoAula = ClassType.builder().id(3L).code("PILATES").name("Pilates").active(true).tenant(tenant).build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        when(bookingConfigService.get()).thenReturn(
                BookingConfig.builder().id(1L).tenant(tenant).cancelCutoffHours(0)
                        .publishDaysBeforeMonth(15).updatedAt(Instant.now()).build());

        TenantContext.set(TENANT_ID, "academia-fit");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a listagem é escopada pela academia da requisição, não só pelo usuário")
    void listaEscopadaPorTenant() {
        when(bookingRepository.findAllByUserIdWithSession(aluno.getId(), TENANT_ID))
                .thenReturn(List.of());

        controller.list("all");

        verify(bookingRepository).findAllByUserIdWithSession(aluno.getId(), TENANT_ID);
    }

    @Test
    @DisplayName("scope=upcoming devolve só as aulas futuras")
    void filtraFuturas() {
        when(bookingRepository.findAllByUserIdWithSession(aluno.getId(), TENANT_ID))
                .thenReturn(List.of(reserva(-48), reserva(24)));

        var corpo = controller.list("upcoming").getBody();

        assertThat(corpo).hasSize(1);
        assertThat(corpo.get(0).getStartAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("scope=past devolve só as aulas que já passaram")
    void filtraPassadas() {
        when(bookingRepository.findAllByUserIdWithSession(aluno.getId(), TENANT_ID))
                .thenReturn(List.of(reserva(-48), reserva(24)));

        var corpo = controller.list("past").getBody();

        assertThat(corpo).hasSize(1);
        assertThat(corpo.get(0).getStartAt()).isBefore(Instant.now());
    }

    @Test
    @DisplayName("scope=all devolve tudo")
    void listaTudo() {
        when(bookingRepository.findAllByUserIdWithSession(aluno.getId(), TENANT_ID))
                .thenReturn(List.of(reserva(-48), reserva(24)));

        assertThat(controller.list("all").getBody()).hasSize(2);
    }

    @Test
    @DisplayName("scope inválido é rejeitado em vez de devolver lista silenciosamente errada")
    void scopeInvalido() {
        when(bookingRepository.findAllByUserIdWithSession(aluno.getId(), TENANT_ID))
                .thenReturn(List.of(reserva(24)));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> controller.list("qualquer-coisa"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a janela de cancelamento configurada é refletida no campo cancellable")
    void refleteJanelaDeCancelamento() {
        when(bookingConfigService.get()).thenReturn(
                BookingConfig.builder().id(1L).tenant(tenant).cancelCutoffHours(48)
                        .publishDaysBeforeMonth(15).updatedAt(Instant.now()).build());
        when(bookingRepository.findAllByUserIdWithSession(aluno.getId(), TENANT_ID))
                .thenReturn(List.of(reserva(24)));   // aula em 24h, corte de 48h

        var corpo = controller.list("upcoming").getBody();

        assertThat(corpo).hasSize(1);
        assertThat(corpo.get(0).isCancellable()).isFalse();
    }

    private Booking reserva(int horasAPartirDeAgora) {
        Instant inicio = Instant.now().plus(horasAPartirDeAgora, ChronoUnit.HOURS);
        var sessao = ClassSession.builder()
                .id(10L).classType(tipoAula).tenant(tenant)
                .startAt(inicio).endAt(inicio.plus(1, ChronoUnit.HOURS))
                .capacity(10).canceled(false)
                .createdByAdminId(1L).createdAt(Instant.now())
                .build();
        return Booking.builder()
                .id(100L).session(sessao).user(aluno).tenant(tenant)
                .status(BookingStatus.BOOKED).createdAt(Instant.now())
                .build();
    }
}
