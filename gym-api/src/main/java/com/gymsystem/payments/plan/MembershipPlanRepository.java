package com.gymsystem.payments.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {

    /** Catalogo publico da academia, na ordem de exibicao. */
    List<MembershipPlan> findByTenantIdAndActiveTrueOrderBySortOrderAscPriceCentsAsc(Long tenantId);

    /** Visao do admin: inclui os inativos. */
    List<MembershipPlan> findByTenantIdOrderBySortOrderAscPriceCentsAsc(Long tenantId);

    /**
     * Busca escopada por academia. Toda leitura de plano passa por aqui: com
     * findById puro, um aluno assinaria o plano barato de outra academia
     * mandando o id dele.
     */
    Optional<MembershipPlan> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByCodeAndTenantId(String code, Long tenantId);
}
