package com.gymsystem.tenant;

import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Associates a User with a Tenant and defines their role within that tenant.
 * One user can have different roles in different tenants.
 */
@Entity
@Table(
    name = "tenant_users",
    uniqueConstraints = @UniqueConstraint(name = "uk_tenant_users", columnNames = {"tenant_id", "user_id"})
)
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Role this user holds within this specific tenant. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TenantRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;
}
