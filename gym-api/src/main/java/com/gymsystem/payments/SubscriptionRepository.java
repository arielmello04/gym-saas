package com.gymsystem.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // ── Usado por SubscriptionService e BookingService ────────
    Optional<Subscription> findByUserIdAndTenantIdAndStatusIn(
            Long userId, Long tenantId, Iterable<SubscriptionStatus> statuses);

    // ── Usado por AdminSubscriptionController e AdminReportsService ─
    List<Subscription> findByTenantIdAndStatusIn(
            Long tenantId, Iterable<SubscriptionStatus> statuses);

    /** Visao administrativa: todas as assinaturas da academia, em qualquer situacao. */
    List<Subscription> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}