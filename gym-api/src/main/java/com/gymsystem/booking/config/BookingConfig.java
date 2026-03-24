package com.gymsystem.booking.config;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "booking_config")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(name = "publish_days_before_month", nullable = false) private int publishDaysBeforeMonth;
    @Column(name = "business_days", length = 32)                  private String businessDays;
    @Column(name = "business_start")                              private LocalTime businessStart;
    @Column(name = "business_end")                                private LocalTime businessEnd;
    @Column(name = "cancel_cutoff_hours",  nullable = false)      private int cancelCutoffHours;
    @Column(name = "one_per_day_per_type", nullable = false)      private boolean onePerDayPerType;

    /** Whether the waitlist feature is active for this tenant. */
    @Column(name = "waitlist_enabled", nullable = false)
    private boolean waitlistEnabled;

    /** Hours the promoted user has to confirm before the spot is given to the next person. */
    @Column(name = "waitlist_promotion_hours", nullable = false)
    private int waitlistPromotionHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
