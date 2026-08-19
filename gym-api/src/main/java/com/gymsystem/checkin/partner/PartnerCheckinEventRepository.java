package com.gymsystem.checkin.partner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PartnerCheckinEventRepository extends JpaRepository<PartnerCheckinEvent, Long> {

    /** O parceiro pode reenviar a mesma notificação; o link é a chave. */
    Optional<PartnerCheckinEvent> findByConfirmUrl(String confirmUrl);

    List<PartnerCheckinEvent> findByTenantIdAndStatusOrderByReceivedAtDesc(
            Long tenantId, PartnerEventStatus status);

    Optional<PartnerCheckinEvent> findByIdAndTenantId(Long id, Long tenantId);

    /** Varredura que fecha os que passaram dos 90 minutos. */
    List<PartnerCheckinEvent> findByStatusAndExpiresAtBefore(PartnerEventStatus status, Instant limite);
}
