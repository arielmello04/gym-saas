package com.gymsystem.tenant;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Represents a gym/studio tenant in the SaaS platform.
 * Each tenant is a completely isolated gym, pilates studio, or similar business.
 */
@Entity
@Table(name = "tenants")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL-safe unique identifier used in subdomain or header (e.g. "academia-fit"). */
    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private boolean active;

    /** Subscription plan of the tenant on the SaaS platform. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TenantPlan plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
