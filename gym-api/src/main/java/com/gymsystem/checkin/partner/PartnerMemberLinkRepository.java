package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerMemberLinkRepository extends JpaRepository<PartnerMemberLink, Long> {

    Optional<PartnerMemberLink> findByTenantIdAndUserIdAndProvider(
            Long tenantId, Long userId, CheckinProvider provider);

    /** Casa o webhook do TotalPass, que identifica a pessoa por CPF. */
    Optional<PartnerMemberLink> findByTenantIdAndProviderAndDocument(
            Long tenantId, CheckinProvider provider, String document);

    Optional<PartnerMemberLink> findByTenantIdAndProviderAndExternalId(
            Long tenantId, CheckinProvider provider, String externalId);

    List<PartnerMemberLink> findByTenantIdAndUserId(Long tenantId, Long userId);

    List<PartnerMemberLink> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);
}
