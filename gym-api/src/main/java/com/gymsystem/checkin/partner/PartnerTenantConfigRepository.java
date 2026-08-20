package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerTenantConfigRepository extends JpaRepository<PartnerTenantConfig, Long> {

    Optional<PartnerTenantConfig> findByTenantIdAndProvider(Long tenantId, CheckinProvider provider);

    List<PartnerTenantConfig> findByTenantId(Long tenantId);
}
