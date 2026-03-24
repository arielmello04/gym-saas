package com.gymsystem.auth.invite;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "signup_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SignupToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64) private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Tenant this invite belongs to — new members join this tenant automatically. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at")                   private Instant expiresAt;
    @Column(name = "max_uses",   nullable = false)  private int maxUses;
    @Column(name = "used_count", nullable = false)  private int usedCount;
    @Column(nullable = false)                        private boolean active;
}
