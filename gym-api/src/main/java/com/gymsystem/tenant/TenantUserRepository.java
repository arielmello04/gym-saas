package com.gymsystem.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    Optional<TenantUser> findByTenantIdAndUserIdAndActiveTrue(Long tenantId, Long userId);

    List<TenantUser> findByUserIdAndActiveTrue(Long userId);

    List<TenantUser> findByTenantIdAndActiveTrue(Long tenantId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);

    /**
     * Vinculo ativo do usuario com a academia, pelo e-mail (que e o principal
     * autenticado). Usado pelo TenantResolutionFilter, que so tem o e-mail em maos.
     */
    @Query("""
        SELECT COUNT(tu) > 0 FROM TenantUser tu
        WHERE tu.tenant.id = :tenantId
          AND tu.user.email = :email
          AND tu.active = true
    """)
    boolean hasActiveMembership(@Param("tenantId") Long tenantId, @Param("email") String email);
}
