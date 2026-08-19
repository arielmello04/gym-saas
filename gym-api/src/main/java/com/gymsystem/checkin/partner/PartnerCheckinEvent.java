package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.Checkin;
import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Check-in que o parceiro empurrou para a academia (hoje, TotalPass).
 *
 * O aluno faz check-in no app e a TotalPass chama a nossa URL com os dados e um
 * link exclusivo de confirmacao. A entrada so e liberada quando alguem faz POST
 * nesse link, dentro de 90 minutos - por isso o evento precisa ficar guardado
 * enquanto espera.
 */
@Entity
@Table(name = "partner_checkin_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PartnerCheckinEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinProvider provider;

    /**
     * Link exclusivo de confirmacao vindo no payload.
     *
     * E a chave natural do evento: o parceiro pode reenviar a mesma notificacao,
     * e ela nao pode virar duas entradas (indice unico em V25).
     */
    @Column(name = "confirm_url", nullable = false, columnDefinition = "TEXT")
    private String confirmUrl;

    @Column(name = "external_user", length = 64)  private String externalUser;
    @Column(name = "user_name",     length = 160) private String userName;
    @Column(name = "user_document", length = 32)  private String userDocument;
    @Column(name = "plan_code",     length = 64)  private String planCode;
    @Column(name = "place_code",    length = 64)  private String placeCode;

    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "expires_at") private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerEventStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** Check-in gerado do nosso lado quando a entrada e liberada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkin_id")
    private Checkin checkin;

    /** Aluno reconhecido pelo CPF do payload, quando ha vinculo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "received_at",  nullable = false) private Instant receivedAt;
    @Column(name = "confirmed_at")                   private Instant confirmedAt;

    /** Passou dos 90 minutos: o parceiro nao aceita mais a confirmacao. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
