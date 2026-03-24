package com.gymsystem.booking.config;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BookingConfigRepository extends JpaRepository<BookingConfig, Long> {

    Optional<BookingConfig> findByTenantId(Long tenantId);
}
