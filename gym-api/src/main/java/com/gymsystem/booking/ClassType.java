package com.gymsystem.booking;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "class_types")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClassType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64) private String code;
    @Column(nullable = false, length = 128)               private String name;
    @Column(columnDefinition = "TEXT")                    private String description;
    @Column(nullable = false)                             private boolean active;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
