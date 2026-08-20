package com.gymsystem.payments.plan;

import com.gymsystem.payments.plan.dto.CreatePlanRequest;
import com.gymsystem.payments.plan.dto.UpdatePlanRequest;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantContext;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Catalogo de planos.
 *
 * A regra central: o preco de uma assinatura sai daqui, do servidor. Antes o
 * corpo da requisicao trazia planName, priceCents e currency, e a assinatura
 * cobrava exatamente o que viesse - dava para assinar o plano anual por R$ 1,00.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipPlanServiceTest {

    private static final Long TENANT_ID = 2L;
    private static final Long OUTRO_TENANT = 99L;

    @Mock private MembershipPlanRepository repository;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private MembershipPlanService service;

    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(TENANT_ID).slug("academia-fit").name("Fit").active(true).build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(repository.save(any(MembershipPlan.class))).thenAnswer(inv -> {
            MembershipPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(50L);
            return p;
        });
        TenantContext.set(TENANT_ID, "academia-fit");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── O que fecha o furo de preço ───────────────────────────

    @Test
    @DisplayName("plano de outra academia é rejeitado: senão bastaria mandar o id do plano barato dela")
    void rejeitaPlanoDeOutraAcademia() {
        // A busca é escopada, então o plano da outra academia simplesmente não aparece.
        when(repository.findByIdAndTenantId(77L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveForSubscription(77L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao encontrado");

        verify(repository).findByIdAndTenantId(77L, TENANT_ID);
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("plano inativo não pode ser assinado")
    void rejeitaPlanoInativo() {
        var inativo = plano("ANTIGO", 9900, false);
        when(repository.findByIdAndTenantId(50L, TENANT_ID)).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> service.requireActiveForSubscription(50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponivel");
    }

    @Test
    @DisplayName("plano ativo devolve o preço do catálogo")
    void devolvePlanoAtivoComPrecoDoCatalogo() {
        when(repository.findByIdAndTenantId(50L, TENANT_ID))
                .thenReturn(Optional.of(plano("ANUAL", 119900, true)));

        var plano = service.requireActiveForSubscription(50L);

        assertThat(plano.getPriceCents()).isEqualTo(119900);
        assertThat(plano.getCurrency()).isEqualTo("BRL");
    }

    // ── Catálogo ──────────────────────────────────────────────

    @Test
    @DisplayName("o aluno vê só os planos ativos")
    void catalogoDoAlunoSoTemAtivos() {
        when(repository.findByTenantIdAndActiveTrueOrderBySortOrderAscPriceCentsAsc(TENANT_ID))
                .thenReturn(List.of(plano("MENSAL", 12900, true)));

        var catalogo = service.listActive();

        assertThat(catalogo).hasSize(1);
        assertThat(catalogo.get(0).active()).isTrue();
        verify(repository).findByTenantIdAndActiveTrueOrderBySortOrderAscPriceCentsAsc(TENANT_ID);
    }

    @Test
    @DisplayName("o admin vê também os inativos, para poder reativar")
    void catalogoDoAdminIncluiInativos() {
        when(repository.findByTenantIdOrderBySortOrderAscPriceCentsAsc(TENANT_ID))
                .thenReturn(List.of(plano("MENSAL", 12900, true), plano("ANTIGO", 9900, false)));

        assertThat(service.listAll()).hasSize(2);
    }

    @Test
    @DisplayName("o preço em reais vem calculado do servidor, para a tela não dividir por 100")
    void exponhePrecoEmReais() {
        when(repository.findByTenantIdAndActiveTrueOrderBySortOrderAscPriceCentsAsc(TENANT_ID))
                .thenReturn(List.of(plano("MENSAL", 12900, true)));

        var item = service.listActive().get(0);

        assertThat(item.priceCents()).isEqualTo(12900);
        assertThat(item.priceReais()).isEqualTo(129.00);
    }

    @Test
    @DisplayName("o rótulo do intervalo acompanha a duração do ciclo")
    void rotuloDoIntervalo() {
        when(repository.findByTenantIdAndActiveTrueOrderBySortOrderAscPriceCentsAsc(TENANT_ID))
                .thenReturn(List.of(
                        plano("MENSAL", 12900, true, 1),
                        plano("TRIMESTRAL", 34900, true, 3),
                        plano("ANUAL", 119900, true, 12)));

        assertThat(service.listActive()).extracting("intervalLabel")
                .containsExactly("mês", "trimestre", "ano");
    }

    // ── Administração ─────────────────────────────────────────

    @Test
    @DisplayName("cria plano normalizando o código para maiúsculas")
    void criaPlanoNormalizandoCodigo() {
        var req = new CreatePlanRequest();
        req.setCode(" mensal ");
        req.setName("Mensal");
        req.setPriceCents(12900L);
        req.setIntervalMonths(1);

        var criado = service.create(req);

        assertThat(criado.code()).isEqualTo("MENSAL");
        assertThat(criado.active()).isTrue();
        assertThat(criado.currency()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("código repetido na mesma academia é recusado")
    void recusaCodigoDuplicado() {
        when(repository.existsByCodeAndTenantId("MENSAL", TENANT_ID)).thenReturn(true);
        var req = new CreatePlanRequest();
        req.setCode("MENSAL");
        req.setName("Mensal");
        req.setPriceCents(12900L);
        req.setIntervalMonths(1);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MENSAL");
    }

    @Test
    @DisplayName("atualização parcial mantém o que não veio no corpo")
    void atualizacaoParcial() {
        var existente = plano("MENSAL", 12900, true);
        when(repository.findByIdAndTenantId(50L, TENANT_ID)).thenReturn(Optional.of(existente));

        var req = new UpdatePlanRequest();
        req.setPriceCents(13900L);   // só o preço

        var atualizado = service.update(50L, req);

        assertThat(atualizado.priceCents()).isEqualTo(13900);
        assertThat(atualizado.name()).isEqualTo("Plano MENSAL");   // preservado
        assertThat(atualizado.active()).isTrue();                   // preservado
    }

    @Test
    @DisplayName("editar plano de outra academia é bloqueado")
    void bloqueiaEdicaoDeOutraAcademia() {
        when(repository.findByIdAndTenantId(77L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(77L, new UpdatePlanRequest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("remover é desativar: assinaturas existentes seguem apontando para o plano")
    void removerApenasDesativa() {
        var existente = plano("MENSAL", 12900, true);
        when(repository.findByIdAndTenantId(50L, TENANT_ID)).thenReturn(Optional.of(existente));

        service.deactivate(50L);

        assertThat(existente.isActive()).isFalse();
        verify(repository).save(existente);
        verify(repository, never()).delete(any());
    }

    // ── Helpers ───────────────────────────────────────────────

    private MembershipPlan plano(String code, long priceCents, boolean active) {
        return plano(code, priceCents, active, 1);
    }

    private MembershipPlan plano(String code, long priceCents, boolean active, int meses) {
        return MembershipPlan.builder()
                .id(50L).tenant(tenant)
                .code(code).name("Plano " + code)
                .description("descricao")
                .priceCents(priceCents).currency("BRL")
                .intervalMonths(meses).active(active).sortOrder(0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
