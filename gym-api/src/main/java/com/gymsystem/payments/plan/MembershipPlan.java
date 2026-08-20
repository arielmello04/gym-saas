package com.gymsystem.payments.plan;

import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Plano de mensalidade oferecido por uma academia.
 *
 * O preco vive aqui, no servidor. A assinatura referencia o plano em vez de
 * receber valor do cliente - antes o corpo da requisicao dizia quanto cobrar.
 */
@Entity
@Table(name = "membership_plans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_membership_plans_tenant_code", columnNames = {"tenant_id", "code"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MembershipPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /** Identificador estavel do plano dentro da academia (ex.: MENSAL). */
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false, length = 8)
    private String currency;

    /** Duracao do ciclo em meses: 1 mensal, 3 trimestral, 12 anual. */
    @Column(name = "interval_months", nullable = false)
    private int intervalMonths;

    /** Plano inativo some do catalogo, mas as assinaturas dele continuam valendo. */
    @Column(nullable = false)
    private boolean active;

    /** Ordem de exibicao no catalogo. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
