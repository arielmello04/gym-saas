package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.Checkin;
import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.checkin.CheckinRepository;
import com.gymsystem.checkin.CheckinStatus;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Check-ins que a TotalPass empurra por webhook.
 *
 * Três coisas sustentam esse fluxo e por isso têm teste: a notificação repetida
 * não pode virar duas entradas; a entrada local só existe depois do parceiro
 * aceitar; e o prazo de 90 minutos precisa fechar sozinho, senão a recepção fica
 * vendo check-ins que o parceiro já não confirma mais.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerCheckinServiceTest {

    private static final Long TENANT_ID = 2L;
    private static final String CPF = "66844563680";
    private static final String CONFIRM_URL = "https://parceiro/webhook_confirmations/tok-1";
    private static final PartnerCredentials CRED = PartnerCredentials.totalpass("place-key");

    @Mock private PartnerCheckinEventRepository eventRepository;
    @Mock private PartnerMemberLinkRepository linkRepository;
    @Mock private CheckinRepository checkinRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private CheckinPartnerRegistry partners;
    @Mock private UserRepository userRepository;
    @Mock private PushPartner totalpass;
    @Mock private PartnerCredentialsService credentials;

    @InjectMocks private PartnerCheckinService service;

    private Tenant tenant;
    private User aluno;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Fit").active(true).build();
        aluno  = User.builder().id(1L).email("aluno@local.test").role(UserRole.USER).active(true).build();

        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(partners.requirePush(any())).thenReturn(totalpass);
        when(credentials.forTenant(any(), any())).thenReturn(CRED);
        when(eventRepository.save(any(PartnerCheckinEvent.class))).thenAnswer(inv -> {
            PartnerCheckinEvent e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });
        when(checkinRepository.save(any(Checkin.class))).thenAnswer(inv -> {
            Checkin c = inv.getArgument(0);
            if (c.getId() == null) c.setId(99L);
            return c;
        });
    }

    // ── Recebimento ───────────────────────────────────────────

    @Test
    @DisplayName("reconhece o aluno pelo CPF quando já existe vínculo")
    void reconheceAlunoPeloCpf() {
        comVinculoPorCpf();

        var evento = service.receive(TENANT_ID, CheckinProvider.TOTALPASS, inbound(90));

        assertThat(evento.getUser()).isEqualTo(aluno);
        assertThat(evento.getStatus()).isEqualTo(PartnerEventStatus.PENDING);
    }

    @Test
    @DisplayName("sem vínculo, o evento entra sem aluno e espera a recepção identificar")
    void semVinculoFicaSemAluno() {
        semVinculoPorCpf();

        var evento = service.receive(TENANT_ID, CheckinProvider.TOTALPASS, inbound(90));

        assertThat(evento.getUser()).isNull();
        assertThat(evento.getUserDocument()).isEqualTo(CPF);
    }

    @Test
    @DisplayName("notificação repetida não vira duas entradas")
    void webhookRepetidoEIdempotente() {
        var jaExiste = evento(PartnerEventStatus.PENDING, 90);
        when(eventRepository.findByConfirmUrl(CONFIRM_URL)).thenReturn(Optional.of(jaExiste));

        var evento = service.receive(TENANT_ID, CheckinProvider.TOTALPASS, inbound(90));

        assertThat(evento).isSameAs(jaExiste);
        verify(eventRepository, never()).save(any());
    }

    // ── Confirmação ───────────────────────────────────────────

    @Test
    @DisplayName("confirma no parceiro e só então grava a entrada")
    void confirmaEGravaEntrada() {
        var evento = evento(PartnerEventStatus.PENDING, 90);
        evento.setUser(aluno);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));
        when(totalpass.confirm(CRED, CONFIRM_URL)).thenReturn(PartnerValidation.approved(null, null, null));

        var resultado = service.confirm(TENANT_ID, 10L, null);

        assertThat(resultado.getStatus()).isEqualTo(PartnerEventStatus.CONFIRMED);
        assertThat(resultado.getConfirmedAt()).isNotNull();

        var captor = ArgumentCaptor.forClass(Checkin.class);
        verify(checkinRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CheckinStatus.COMPLETED);
        assertThat(captor.getValue().getUser()).isEqualTo(aluno);
    }

    @Test
    @DisplayName("parceiro recusa: nada de entrada local, e o motivo fica registrado")
    void recusaNaoGeraEntrada() {
        var evento = evento(PartnerEventStatus.PENDING, 90);
        evento.setUser(aluno);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));
        when(totalpass.confirm(any(), any()))
                .thenReturn(PartnerValidation.denied("Check-in ja validado no portal da TotalPass"));

        var resultado = service.confirm(TENANT_ID, 10L, null);

        assertThat(resultado.getStatus()).isEqualTo(PartnerEventStatus.FAILED);
        assertThat(resultado.getFailureReason()).contains("ja validado");
        verify(checkinRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmar duas vezes não gera duas entradas")
    void confirmarDuasVezes() {
        var evento = evento(PartnerEventStatus.CONFIRMED, 90);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));

        service.confirm(TENANT_ID, 10L, null);

        verify(totalpass, never()).confirm(any(), any());
        verify(checkinRepository, never()).save(any());
    }

    @Test
    @DisplayName("fora do prazo de 90 minutos, expira em vez de tentar confirmar")
    void foraDoPrazo() {
        var evento = evento(PartnerEventStatus.PENDING, -10); // venceu há 10 min
        evento.setUser(aluno);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));

        var resultado = service.confirm(TENANT_ID, 10L, null);

        assertThat(resultado.getStatus()).isEqualTo(PartnerEventStatus.EXPIRED);
        verify(totalpass, never()).confirm(any(), any());
    }

    // ── Primeira visita ───────────────────────────────────────

    @Test
    @DisplayName("aluno não identificado: erro claro pedindo o vínculo, sem chamar o parceiro")
    void semAlunoPedeVinculo() {
        var evento = evento(PartnerEventStatus.PENDING, 90);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.confirm(TENANT_ID, 10L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CPF);

        verify(totalpass, never()).confirm(any(), any());
    }

    @Test
    @DisplayName("informando o e-mail, cria o vínculo e libera — a próxima visita já é reconhecida")
    void vinculaNaPrimeiraVisita() {
        var evento = evento(PartnerEventStatus.PENDING, 90);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));
        when(userRepository.findByEmail("aluno@local.test")).thenReturn(Optional.of(aluno));
        when(linkRepository.findByTenantIdAndUserIdAndProvider(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(totalpass.confirm(any(), any())).thenReturn(PartnerValidation.approved(null, null, null));

        var resultado = service.confirm(TENANT_ID, 10L, "aluno@local.test");

        assertThat(resultado.getStatus()).isEqualTo(PartnerEventStatus.CONFIRMED);

        var captor = ArgumentCaptor.forClass(PartnerMemberLink.class);
        verify(linkRepository).save(captor.capture());
        assertThat(captor.getValue().getDocument()).isEqualTo(CPF);
        assertThat(captor.getValue().getUser()).isEqualTo(aluno);
    }

    @Test
    @DisplayName("e-mail desconhecido é rejeitado")
    void emailDesconhecido() {
        var evento = evento(PartnerEventStatus.PENDING, 90);
        when(eventRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(evento));
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(TENANT_ID, 10L, "nao.existe@local.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Expiração automática ──────────────────────────────────

    @Test
    @DisplayName("a varredura fecha os que passaram do prazo")
    void varreduraExpira() {
        var vencido = evento(PartnerEventStatus.PENDING, -5);
        when(eventRepository.findByStatusAndExpiresAtBefore(eq(PartnerEventStatus.PENDING), any()))
                .thenReturn(List.of(vencido));

        service.expirarPendentes();

        assertThat(vencido.getStatus()).isEqualTo(PartnerEventStatus.EXPIRED);
        verify(eventRepository).save(vencido);
    }

    @Test
    @DisplayName("sem vencidos, a varredura não faz nada")
    void varreduraSemVencidos() {
        when(eventRepository.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(List.of());

        service.expirarPendentes();

        verify(eventRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────

    private PartnerCheckinService.InboundCheckin inbound(int minutosAteExpirar) {
        Instant agora = Instant.now();
        return new PartnerCheckinService.InboundCheckin(
                CONFIRM_URL, "EQ2B3FBK", "Cleveland Wolf", CPF, "59TADO9F", "59TADO9F",
                agora, agora.plus(minutosAteExpirar, ChronoUnit.MINUTES));
    }

    private PartnerCheckinEvent evento(PartnerEventStatus status, int minutosAteExpirar) {
        Instant agora = Instant.now();
        return PartnerCheckinEvent.builder()
                .id(10L).tenant(tenant).provider(CheckinProvider.TOTALPASS)
                .confirmUrl(CONFIRM_URL)
                .externalUser("EQ2B3FBK").userName("Cleveland Wolf").userDocument(CPF)
                .planCode("59TADO9F").status(status)
                .startedAt(agora)
                .expiresAt(agora.plus(minutosAteExpirar, ChronoUnit.MINUTES))
                .receivedAt(agora)
                .build();
    }

    private void comVinculoPorCpf() {
        var link = PartnerMemberLink.builder()
                .id(1L).tenant(tenant).user(aluno).provider(CheckinProvider.TOTALPASS)
                .externalId("EQ2B3FBK").document(CPF)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(linkRepository.findByTenantIdAndProviderAndDocument(TENANT_ID, CheckinProvider.TOTALPASS, CPF))
                .thenReturn(Optional.of(link));
    }

    private void semVinculoPorCpf() {
        when(linkRepository.findByTenantIdAndProviderAndDocument(any(), any(), any()))
                .thenReturn(Optional.empty());
    }
}
