package com.gymsystem.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    Optional<TenantUser> findByTenantIdAndUserIdAndActiveTrue(Long tenantId, Long userId);

    List<TenantUser> findByUserIdAndActiveTrue(Long userId);

    List<TenantUser> findByTenantIdAndActiveTrue(Long tenantId);

    boolean existsByTenantIdAndUserId(Long tenantId, Long userId);
}
