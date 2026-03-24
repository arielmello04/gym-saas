package com.gymsystem.auth.invite;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SignupTokenRepository extends JpaRepository<SignupToken, Long> {

    Optional<SignupToken> findByCodeAndActiveTrue(String code);

    List<SignupToken> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
