package com.gymsystem.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProfilePreferencesRepository extends JpaRepository<ProfilePreferences, Long> {
    Optional<ProfilePreferences> findByUserId(Long userId);
    Optional<ProfilePreferences> findByUserIdAndTenantId(Long userId, Long tenantId);
}
