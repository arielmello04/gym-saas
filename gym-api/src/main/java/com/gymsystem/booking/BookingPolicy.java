package com.gymsystem.booking;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "booking_policies")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BookingPolicy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "open_days_in_advance", nullable = false) private int openDaysInAdvance;
    @Column(name = "created_by_admin_id",  nullable = false) private Long createdByAdminId;
    @Column(name = "created_at",           nullable = false) private Instant createdAt;
    @Column(name = "updated_at",           nullable = false) private Instant updatedAt;
}
