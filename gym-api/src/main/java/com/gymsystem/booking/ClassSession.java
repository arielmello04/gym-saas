package com.gymsystem.booking;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "class_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClassSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_type_id", nullable = false)
    private ClassType classType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at",   nullable = false) private Instant endAt;
    @Column(nullable = false)                    private int capacity;
    @Column(nullable = false)                    private boolean canceled;
    private String notes;

    @Column(name = "created_by_admin_id", nullable = false) private Long createdByAdminId;
    @Column(name = "created_at",          nullable = false) private Instant createdAt;
}
