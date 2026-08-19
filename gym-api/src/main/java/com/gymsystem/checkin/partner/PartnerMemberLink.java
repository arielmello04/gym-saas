package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Quem o aluno e do lado do parceiro.
 *
 * Os dois parceiros validam por identidade, nao por codigo digitado: o Wellhub
 * quer o gympass_id de 13 digitos, e o TotalPass identifica a pessoa por CPF no
 * webhook. Sem este vinculo nao ha como ligar a entrada ao aluno certo.
 */
@Entity
@Table(name = "partner_member_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_partner_link_tenant_user_provider",
                columnNames = {"tenant_id", "user_id", "provider"}),
        @UniqueConstraint(name = "uk_partner_link_tenant_provider_external",
                columnNames = {"tenant_id", "provider", "external_id"})
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerMemberLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinProvider provider;

    /** Wellhub: gympass_id. TotalPass: o code do usuario no payload. */
    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    /** Wellhub: PIN ou QR interno da academia associado ao aluno la. */
    @Column(name = "custom_code", length = 13)
    private String customCode;

    /** CPF, como o TotalPass manda no webhook. */
    @Column(length = 32)
    private String document;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
