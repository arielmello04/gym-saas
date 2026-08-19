package com.gymsystem.checkin;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "checkins")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Checkin {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinProvider provider;

    @Column(name = "gym_name",     length = 120) private String gymName;
    @Column(name = "provider_ref", length = 120) private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinStatus status;

    /** Identificacao do aluno no parceiro, para conciliar com o repasse deles. */
    @Column(name = "partner_member_ref", length = 120) private String partnerMemberRef;
    @Column(name = "partner_plan",       length = 120) private String partnerPlan;

    /** Motivo da recusa, quando o parceiro nao autoriza a entrada. */
    @Column(name = "failure_reason",     length = 255) private String failureReason;

    @Column(name = "started_at",   nullable = false) private Instant startedAt;
    @Column(name = "completed_at")                   private Instant completedAt;
}
