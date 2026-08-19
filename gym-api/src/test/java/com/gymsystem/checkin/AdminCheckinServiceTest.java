package com.gymsystem.checkin;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Conciliacao de check-ins com Wellhub e TotalPass.
 *
 * Os parceiros pagam por visita e mandam a contagem do lado deles. O que mais
 * gera divergencia sao as recusas, entao elas precisam aparecer separadas no
 * resumo - nao somadas nem escondidas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminCheckinServiceTest {

    private static final Long TENANT_ID = 2L;

    @Mock private CheckinRepository repository;
    @InjectMocks private AdminCheckinService service;

    private final Instant de  = Instant.now().minus(30, ChronoUnit.DAYS);
    private final Instant ate = Instant.now();

    private User aluno;

    @BeforeEach
    void setUp() {
        var tenant = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Fit").active(true).build();
        aluno = User.builder().id(1L).email("aluno@local.test").role(UserRole.USER).active(true).build();
        TenantContext.set(TENANT_ID, "academia-fit");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("o resumo separa aprovados, recusados e pendentes por parceiro")
    void resumoSeparaPorSituacao() {
        comCheckins(
                checkin(CheckinProvider.WELLHUB,   CheckinStatus.COMPLETED),
                checkin(CheckinProvider.WELLHUB,   CheckinStatus.COMPLETED),
                checkin(CheckinProvider.WELLHUB,   CheckinStatus.FAILED),
                checkin(CheckinProvider.TOTALPASS, CheckinStatus.COMPLETED),
                checkin(CheckinProvider.TOTALPASS, CheckinStatus.STARTED));

        var resumo = service.summary(de, ate);

        var wellhub = linha(resumo, "WELLHUB");
        assertThat(wellhub.completed()).isEqualTo(2);
        assertThat(wellhub.failed()).isEqualTo(1);
        assertThat(wellhub.pending()).isZero();
        assertThat(wellhub.total()).isEqualTo(3);

        var totalpass = linha(resumo, "TOTALPASS");
        assertThat(totalpass.completed()).isEqualTo(1);
        assertThat(totalpass.pending()).isEqualTo(1);
        assertThat(totalpass.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("parceiro sem movimento aparece zerado, em vez de sumir da lista")
    void parceiroSemMovimentoApareceZerado() {
        comCheckins(checkin(CheckinProvider.WELLHUB, CheckinStatus.COMPLETED));

        var resumo = service.summary(de, ate);

        assertThat(resumo).extracting(AdminCheckinService.ProviderSummary::provider)
                .contains("WELLHUB", "TOTALPASS", "DIRECT");
        assertThat(linha(resumo, "TOTALPASS").total()).isZero();
    }

    @Test
    @DisplayName("o total confere com a soma das situações")
    void totalConfere() {
        comCheckins(
                checkin(CheckinProvider.WELLHUB, CheckinStatus.COMPLETED),
                checkin(CheckinProvider.WELLHUB, CheckinStatus.FAILED),
                checkin(CheckinProvider.WELLHUB, CheckinStatus.STARTED));

        var wellhub = linha(service.summary(de, ate), "WELLHUB");

        assertThat(wellhub.total())
                .isEqualTo(wellhub.completed() + wellhub.failed() + wellhub.pending());
    }

    @Test
    @DisplayName("a listagem filtra por parceiro quando pedido")
    void listaFiltraPorParceiro() {
        comCheckins(
                checkin(CheckinProvider.WELLHUB,   CheckinStatus.COMPLETED),
                checkin(CheckinProvider.TOTALPASS, CheckinStatus.COMPLETED));

        var lista = service.list(de, ate, CheckinProvider.WELLHUB);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).provider()).isEqualTo("WELLHUB");
    }

    @Test
    @DisplayName("sem filtro, a listagem traz todos os parceiros")
    void listaSemFiltro() {
        comCheckins(
                checkin(CheckinProvider.WELLHUB,   CheckinStatus.COMPLETED),
                checkin(CheckinProvider.TOTALPASS, CheckinStatus.COMPLETED));

        assertThat(service.list(de, ate, null)).hasSize(2);
    }

    @Test
    @DisplayName("a listagem é escopada pela academia da requisição")
    void listaEscopadaPorTenant() {
        comCheckins();

        service.list(de, ate, null);

        verify(repository).findByTenantIdAndStartedAtBetweenOrderByStartedAtDesc(
                eq(TENANT_ID), any(), any());
    }

    @Test
    @DisplayName("a linha administrativa mostra o motivo da recusa e a referência no parceiro")
    void linhaMostraMotivoEReferencia() {
        var recusado = checkin(CheckinProvider.WELLHUB, CheckinStatus.FAILED);
        recusado.setFailureReason("Plano sem check-in disponivel");
        recusado.setPartnerMemberRef("wh-777");
        comCheckins(recusado);

        var item = service.list(de, ate, null).get(0);

        assertThat(item.failureReason()).isEqualTo("Plano sem check-in disponivel");
        assertThat(item.partnerMemberRef()).isEqualTo("wh-777");
        assertThat(item.memberEmail()).isEqualTo("aluno@local.test");
    }

    // ── Helpers ───────────────────────────────────────────────

    private void comCheckins(Checkin... checkins) {
        when(repository.findByTenantIdAndStartedAtBetweenOrderByStartedAtDesc(any(), any(), any()))
                .thenReturn(List.of(checkins));
    }

    private Checkin checkin(CheckinProvider provider, CheckinStatus status) {
        return Checkin.builder()
                .id(1L)
                .provider(provider)
                .status(status)
                .user(aluno)
                .providerRef("CHK-" + provider + "-" + status)
                .startedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .completedAt(status == CheckinStatus.COMPLETED ? Instant.now() : null)
                .build();
    }

    private AdminCheckinService.ProviderSummary linha(
            List<AdminCheckinService.ProviderSummary> resumo, String provider) {
        return resumo.stream()
                .filter(r -> r.provider().equals(provider))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Sem linha para " + provider));
    }
}
