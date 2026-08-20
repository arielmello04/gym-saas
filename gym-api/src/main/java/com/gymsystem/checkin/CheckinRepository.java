package com.gymsystem.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {

    List<Checkin> findByUserIdAndTenantIdOrderByStartedAtDesc(Long userId, Long tenantId);

    Optional<Checkin> findByProviderRefAndTenantId(String providerRef, Long tenantId);

    /**
     * Lookup usado pelo callback do provedor, que chega sem tenant no contexto.
     * providerRef e um UUID unico global (indice unico em V20), entao o proprio
     * registro diz a que academia pertence.
     */
    Optional<Checkin> findByProviderRef(String providerRef);

    /** Conciliacao com o parceiro: tudo que aconteceu na academia no periodo. */
    List<Checkin> findByTenantIdAndStartedAtBetweenOrderByStartedAtDesc(
            Long tenantId, Instant from, Instant to);
}
