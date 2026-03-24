package com.gymsystem.booking.config;

import com.gymsystem.tenant.context.TenantGuard;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BookingConfigService {

    private final BookingConfigRepository repo;

    /** Returns the config for the current tenant (throws if missing). */
    public BookingConfig get() {
        Long tenantId = TenantGuard.currentTenantId();
        return repo.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Booking config missing for tenant: " + tenantId));
    }

    @Transactional
    public BookingConfig update(BookingConfig updated) {
        BookingConfig current = get();
        current.setPublishDaysBeforeMonth(updated.getPublishDaysBeforeMonth());
        current.setBusinessDays(updated.getBusinessDays());
        current.setBusinessStart(updated.getBusinessStart());
        current.setBusinessEnd(updated.getBusinessEnd());
        current.setCancelCutoffHours(updated.getCancelCutoffHours());
        current.setOnePerDayPerType(updated.isOnePerDayPerType());
        current.setUpdatedAt(Instant.now());
        return repo.save(current);
    }
}
