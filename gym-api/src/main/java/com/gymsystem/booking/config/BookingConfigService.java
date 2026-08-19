package com.gymsystem.booking.config;

import com.gymsystem.booking.config.dto.UpdateBookingConfigRequest;
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

    /**
     * Applies the writable fields to the current tenant's config.
     *
     * Takes a request DTO rather than the entity: binding the entity from the
     * body let a caller set id and tenant too.
     */
    @Transactional
    public BookingConfig update(UpdateBookingConfigRequest req) {
        BookingConfig current = get();
        current.setPublishDaysBeforeMonth(req.getPublishDaysBeforeMonth());
        current.setBusinessDays(req.getBusinessDays());
        current.setBusinessStart(req.getBusinessStart());
        current.setBusinessEnd(req.getBusinessEnd());
        current.setCancelCutoffHours(req.getCancelCutoffHours());
        current.setOnePerDayPerType(req.getOnePerDayPerType());
        current.setWaitlistEnabled(req.getWaitlistEnabled());
        current.setWaitlistPromotionHours(req.getWaitlistPromotionHours());
        return save(current);
    }

    @Transactional
    public BookingConfig setOnePerDayPerType(boolean enabled) {
        BookingConfig current = get();
        current.setOnePerDayPerType(enabled);
        return save(current);
    }

    @Transactional
    public BookingConfig setCancelCutoffHours(int hours) {
        BookingConfig current = get();
        current.setCancelCutoffHours(Math.max(0, hours));
        return save(current);
    }

    private BookingConfig save(BookingConfig cfg) {
        cfg.setUpdatedAt(Instant.now());
        return repo.save(cfg);
    }
}
