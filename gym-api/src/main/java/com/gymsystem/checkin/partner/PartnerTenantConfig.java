package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.tenant.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Credencial de parceiro que pertence a UMA academia.
 *
 * O token do Wellhub e a partner_api_key da TotalPass identificam a integradora
 * e continuam em configuração global. O que muda de academia para academia é o
 * X-Gym-Id (Wellhub) e a place_api_key (TotalPass) — e é isso que mora aqui.
 */
@Entity
@Table(name = "partner_tenant_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_partner_tenant_config", columnNames = {"tenant_id", "provider"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerTenantConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinProvider provider;

    /** Wellhub: header X-Gym-Id desta unidade. Não é segredo. */
    @Column(name = "gym_id", length = 128)
    private String gymId;

    /** TotalPass: chave da unidade, gerada pela academia no portal. É segredo. */
    @Column(name = "place_api_key", length = 255)
    private String placeApiKey;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
