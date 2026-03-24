package com.gymsystem.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {

    List<Checkin> findByUserIdAndTenantIdOrderByStartedAtDesc(Long userId, Long tenantId);

    Optional<Checkin> findByProviderRefAndTenantId(String providerRef, Long tenantId);
}
