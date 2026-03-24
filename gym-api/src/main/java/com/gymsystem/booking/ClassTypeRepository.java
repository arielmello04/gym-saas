package com.gymsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassTypeRepository extends JpaRepository<ClassType, Long> {

    /** Used by BookingService and MonthlyScheduleGenerator */
    Optional<ClassType> findByCodeAndActiveTrueAndTenantId(String code, Long tenantId);

    /** Used by AdminClassTypeController */
    Optional<ClassType> findByCode(String code);

    /** List all active class types for a tenant */
    List<ClassType> findByTenantIdAndActiveTrue(Long tenantId);

    /** Check if code already exists in tenant */
    boolean existsByCodeAndTenantId(String code, Long tenantId);
}
