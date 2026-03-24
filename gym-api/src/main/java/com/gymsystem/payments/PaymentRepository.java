package com.gymsystem.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    Optional<Payment> findByProviderRef(String providerRef);

    Payment findTopBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);

    long countBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = com.gymsystem.payments.PaymentStatus.PENDING
          AND p.dueAt <= :now
        ORDER BY p.dueAt ASC
    """)
    List<Payment> findDuePending(Instant now);

    /** Used by AdminReportsService */
    List<Payment> findByTenantIdAndStatusAndDueAtBetween(
            Long tenantId, PaymentStatus status, Instant from, Instant to);
}
