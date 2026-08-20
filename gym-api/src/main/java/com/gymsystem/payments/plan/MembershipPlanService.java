package com.gymsystem.payments.plan;

import com.gymsystem.payments.plan.dto.CreatePlanRequest;
import com.gymsystem.payments.plan.dto.PlanResponse;
import com.gymsystem.payments.plan.dto.UpdatePlanRequest;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Catalogo de planos da academia. */
@Service
@RequiredArgsConstructor
public class MembershipPlanService {

    private final MembershipPlanRepository repository;
    private final TenantRepository tenantRepository;

    /** Catalogo que o aluno ve: so os planos ativos. */
    public List<PlanResponse> listActive() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByTenantIdAndActiveTrueOrderBySortOrderAscPriceCentsAsc(tenantId)
                .stream().map(PlanResponse::from).toList();
    }

    /** Visao do admin: inclui inativos, para poder reativar. */
    public List<PlanResponse> listAll() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByTenantIdOrderBySortOrderAscPriceCentsAsc(tenantId)
                .stream().map(PlanResponse::from).toList();
    }

    /**
     * Plano ativo da academia atual, para assinar.
     *
     * A busca e escopada por tenant de proposito: com findById puro, um aluno
     * assinaria o plano barato de outra academia mandando o id dele.
     */
    public MembershipPlan requireActiveForSubscription(Long planId) {
        Long tenantId = TenantGuard.currentTenantId();
        MembershipPlan plan = repository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plano nao encontrado: " + planId));
        if (!plan.isActive()) {
            throw new IllegalStateException("Plano indisponivel: " + plan.getName());
        }
        return plan;
    }

    @Transactional
    public PlanResponse create(CreatePlanRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();

        String code = req.getCode().trim().toUpperCase();
        if (repository.existsByCodeAndTenantId(code, tenantId)) {
            throw new IllegalArgumentException("Ja existe um plano com o codigo " + code);
        }

        Instant now = Instant.now();
        var plan = MembershipPlan.builder()
                .tenant(tenant)
                .code(code)
                .name(req.getName().trim())
                .description(req.getDescription())
                .priceCents(req.getPriceCents())
                .currency(req.getCurrency() == null || req.getCurrency().isBlank()
                        ? "BRL" : req.getCurrency().trim().toUpperCase())
                .intervalMonths(req.getIntervalMonths())
                .active(req.getActive() == null || req.getActive())
                .sortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return PlanResponse.from(repository.save(plan));
    }

    @Transactional
    public PlanResponse update(Long id, UpdatePlanRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        MembershipPlan plan = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plano nao encontrado: " + id));

        if (req.getName() != null)           plan.setName(req.getName().trim());
        if (req.getDescription() != null)    plan.setDescription(req.getDescription());
        if (req.getPriceCents() != null)     plan.setPriceCents(req.getPriceCents());
        if (req.getIntervalMonths() != null) plan.setIntervalMonths(req.getIntervalMonths());
        if (req.getActive() != null)         plan.setActive(req.getActive());
        if (req.getSortOrder() != null)      plan.setSortOrder(req.getSortOrder());
        plan.setUpdatedAt(Instant.now());

        return PlanResponse.from(repository.save(plan));
    }

    /**
     * Desativa em vez de apagar: assinaturas existentes apontam para o plano, e
     * o historico de cobranca precisa continuar fazendo sentido.
     */
    @Transactional
    public void deactivate(Long id) {
        Long tenantId = TenantGuard.currentTenantId();
        MembershipPlan plan = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Plano nao encontrado: " + id));
        plan.setActive(false);
        plan.setUpdatedAt(Instant.now());
        repository.save(plan);
    }
}
