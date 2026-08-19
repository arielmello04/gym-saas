package com.gymsystem.checkin;

import com.gymsystem.checkin.dto.StartCheckinRequest;
import com.gymsystem.checkin.partner.*;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Check-in do lado da academia.
 *
 * O Wellhub é consultado com o identificador do aluno guardado no vínculo — não
 * com um código digitado na recepção, como a primeira versão assumia. E o
 * TotalPass não passa por aqui: a entrada dele chega por webhook.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckinServiceTest {

    private static final String SECRET = "segredo-do-callback";
    private static final Long   TENANT_ID = 7L;
    private static final String EMAIL = "aluno@local.test";
    private static final String WELLHUB_ID = "1234567890123";
    private static final PartnerCredentials CRED = PartnerCredentials.wellhub("gym-42");

    @Mock private CheckinRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private CheckinPartnerRegistry partners;
    @Mock private PartnerCheckinService partnerCheckins;
    @Mock private ValidatingPartner wellhub;
    @Mock private PartnerCredentialsService credentials;

    @InjectMocks private CheckinService service;

    private User aluno;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "callbackSecret", SECRET);

        aluno  = User.builder().id(1L).email(EMAIL).role(UserRole.USER).active(true).build();
        tenant = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Fit").active(true).build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(aluno));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(partners.requireValidating(any())).thenReturn(wellhub);
        when(credentials.forTenant(any(), any())).thenReturn(CRED);
        when(repository.save(any(Checkin.class))).thenAnswer(inv -> {
            Checkin c = inv.getArgument(0);
            if (c.getId() == null) c.setId(42L);
            return c;
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

    // ── Wellhub ───────────────────────────────────────────────

    @Test
    @DisplayName("usa o Wellhub ID do vínculo: a recepção não digita nada")
    void usaOVinculoDoAluno() {
        comVinculo(WELLHUB_ID, "PIN4242");
        when(wellhub.validate(CRED, WELLHUB_ID, "PIN4242"))
                .thenReturn(PartnerValidation.approved(WELLHUB_ID, "Ariel", "Silver"));

        var response = service.start(request("WELLHUB", null));

        assertThat(response.isApproved()).isTrue();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        verify(wellhub).validate(CRED, WELLHUB_ID, "PIN4242");

        Checkin salvo = ultimoSalvo();
        assertThat(salvo.getPartnerMemberRef()).isEqualTo(WELLHUB_ID);
        assertThat(salvo.getPartnerPlan()).isEqualTo("Silver");
    }

    @Test
    @DisplayName("o ID informado na requisição tem prioridade: é a primeira visita")
    void idInformadoTemPrioridade() {
        comVinculo(WELLHUB_ID, null);
        when(wellhub.validate(CRED, "9999999999999", null))
                .thenReturn(PartnerValidation.approved("9999999999999", null, null));

        service.start(request("WELLHUB", "9999999999999"));

        verify(wellhub).validate(CRED, "9999999999999", null);
    }

    @Test
    @DisplayName("aluno sem vínculo e sem ID informado: erro claro, sem chamar o parceiro")
    void semVinculoNemId() {
        semVinculo();

        var response = service.start(request("WELLHUB", null));

        assertThat(response.isApproved()).isFalse();
        assertThat(response.getMessage()).contains("Wellhub ID");
        verify(wellhub, never()).validate(any(), any(), any());
        assertThat(ultimoSalvo().getStatus()).isEqualTo(CheckinStatus.FAILED);
    }

    @Test
    @DisplayName("recusa do parceiro vira FAILED com o motivo, sem exceção")
    void recusaDoParceiro() {
        comVinculo(WELLHUB_ID, null);
        when(wellhub.validate(any(), any(), any()))
                .thenReturn(PartnerValidation.denied("Check-in nao encontrado"));

        var response = service.start(request("WELLHUB", null));

        assertThat(response.isApproved()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Check-in nao encontrado");

        Checkin salvo = ultimoSalvo();
        assertThat(salvo.getStatus()).isEqualTo(CheckinStatus.FAILED);
        assertThat(salvo.getFailureReason()).isEqualTo("Check-in nao encontrado");
        // Guarda o ID tentado: é o que se confere com o parceiro depois.
        assertThat(salvo.getPartnerMemberRef()).isEqualTo(WELLHUB_ID);
    }

    @Test
    @DisplayName("\"GYMPASS\" do cliente antigo continua caindo no Wellhub")
    void gympassVaiParaWellhub() {
        comVinculo(WELLHUB_ID, null);
        when(wellhub.validate(any(), any(), any())).thenReturn(PartnerValidation.approved(WELLHUB_ID, null, null));

        service.start(request("GYMPASS", null));

        verify(partners).requireValidating(CheckinProvider.WELLHUB);
    }

    // ── TotalPass ─────────────────────────────────────────────

    @Test
    @DisplayName("TotalPass não pode ser iniciado aqui: a entrada dele chega por webhook")
    void totalpassNaoPassaPorAqui() {
        assertThatThrownBy(() -> service.start(request("TOTALPASS", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("webhook");

        verify(repository, never()).save(any());
    }

    // ── Check-in direto ───────────────────────────────────────

    @Test
    @DisplayName("check-in direto conclui na hora e não consulta parceiro nenhum")
    void checkinDireto() {
        var req = request("DIRECT", null);
        req.setGymName("Unidade Centro");

        var response = service.start(req);

        assertThat(response.isApproved()).isTrue();
        verify(partners, never()).requireValidating(any());
        assertThat(ultimoSalvo().getGymName()).isEqualTo("Unidade Centro");
    }

    // ── Callback ──────────────────────────────────────────────

    @Test
    @DisplayName("callback com segredo errado é rejeitado")
    void callbackComSegredoErrado() {
        assertThatThrownBy(() -> service.providerCallback("errado", "CHK-1", true))
                .isInstanceOf(SecurityException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("callback busca pela referência sozinha: chega sem tenant no contexto")
    void callbackSemTenant() {
        var existente = Checkin.builder()
                .id(5L).providerRef("CHK-1").status(CheckinStatus.STARTED)
                .provider(CheckinProvider.WELLHUB).startedAt(Instant.now()).build();
        when(repository.findByProviderRef("CHK-1")).thenReturn(Optional.of(existente));

        TenantContext.clear();
        service.providerCallback(SECRET, "CHK-1", true);

        assertThat(existente.getStatus()).isEqualTo(CheckinStatus.COMPLETED);
    }

    // ── Helpers ───────────────────────────────────────────────

    private StartCheckinRequest request(String provider, String code) {
        var req = new StartCheckinRequest();
        req.setProvider(provider);
        req.setCode(code);
        return req;
    }

    private void comVinculo(String externalId, String customCode) {
        var link = PartnerMemberLink.builder()
                .id(1L).tenant(tenant).user(aluno).provider(CheckinProvider.WELLHUB)
                .externalId(externalId).customCode(customCode)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(partnerCheckins.link(eq(TENANT_ID), eq(aluno.getId()), any()))
                .thenReturn(Optional.of(link));
    }

    private void semVinculo() {
        when(partnerCheckins.link(any(), any(), any())).thenReturn(Optional.empty());
    }

    private Checkin ultimoSalvo() {
        var captor = ArgumentCaptor.forClass(Checkin.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
