package com.gymsystem.payments;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "amount_cents", nullable = false)             private long amountCents;
    @Column(nullable = false, length = 8)                        private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(nullable = false, length = 32)   private String provider;
    @Column(name = "provider_ref", length = 128) private String providerRef;
    @Column(name = "due_at",     nullable = false) private Instant dueAt;
    @Column(name = "paid_at")                      private Instant paidAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "last_attempt_at")             private Instant lastAttemptAt;
}
