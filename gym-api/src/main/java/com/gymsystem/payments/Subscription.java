package com.gymsystem.payments;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "subscriptions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Subscription {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "plan_name",  nullable = false, length = 64) private String planName;
    @Column(name = "price_cents", nullable = false)             private long priceCents;
    @Column(nullable = false, length = 8)                       private String currency;
    @Column(name = "billing_day", nullable = false)             private int billingDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false) private Instant currentPeriodStart;
    @Column(name = "current_period_end",   nullable = false) private Instant currentPeriodEnd;
    @Column(name = "next_billing_at",      nullable = false) private Instant nextBillingAt;
    @Column(name = "created_at",           nullable = false) private Instant createdAt;
    @Column(name = "canceled_at")                            private Instant canceledAt;
}
