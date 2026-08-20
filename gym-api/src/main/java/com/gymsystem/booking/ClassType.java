package com.gymsystem.booking;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "class_types", uniqueConstraints = {
        @UniqueConstraint(name = "uk_class_types_tenant_code", columnNames = {"tenant_id", "code"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ClassType {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)                private String code;
    @Column(nullable = false, length = 128)               private String name;
    @Column(columnDefinition = "TEXT")                    private String description;
    @Column(nullable = false)                             private boolean active;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
