package com.gymsystem.payments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // ── Usado por SubscriptionService e BookingService ────────
    Optional<Subscription> findByUserIdAndTenantIdAndStatusIn(
            Long userId, Long tenantId, Iterable<SubscriptionStatus> statuses);

    // ── Usado por BookingEnforcer (sem tenantId) ──────────────
    Optional<Subscription> findByUserIdAndStatusIn(
            Long userId, Iterable<SubscriptionStatus> statuses);

    // ── Usado por AdminPaymentController e AdminReportsService ─
    List<Subscription> findByTenantIdAndStatusIn(
            Long tenantId, Iterable<SubscriptionStatus> statuses);
}