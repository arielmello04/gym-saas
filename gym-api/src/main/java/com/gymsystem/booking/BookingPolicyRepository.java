package com.gymsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingPolicyRepository extends JpaRepository<BookingPolicy, Long> {

    // ── Usado por BookingService e CalendarController ─────────
    Optional<BookingPolicy> findTopByTenantIdOrderByIdAsc(Long tenantId);

    // ── Compatibilidade retroativa (sem tenantId) ─────────────
    Optional<BookingPolicy> findTopByOrderByIdAsc();
}