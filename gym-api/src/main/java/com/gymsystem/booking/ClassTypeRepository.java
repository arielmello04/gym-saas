package com.gymsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassTypeRepository extends JpaRepository<ClassType, Long> {

    /** Used by BookingService and MonthlyScheduleGenerator */
    Optional<ClassType> findByCodeAndActiveTrueAndTenantId(String code, Long tenantId);

    /**
     * Lookup escopado por tenant. Nao existe mais findByCode global: com o codigo
     * unico por tenant (V21) ele passou a ser ambiguo entre academias.
     */
    Optional<ClassType> findByIdAndTenantId(Long id, Long tenantId);

    /** List all active class types for a tenant */
    List<ClassType> findByTenantIdAndActiveTrue(Long tenantId);

    /** Check if code already exists in tenant */
    boolean existsByCodeAndTenantId(String code, Long tenantId);
}
