package com.gymsystem.booking;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * Cancels a user's future bookings within a tenant when subscription becomes PAST_DUE.
 */
@Component
@RequiredArgsConstructor
public class BookingEnforcer {

    private final BookingRepository bookingRepository;

    @Value("${payments.past-due.cancel-future-bookings:true}")
    private boolean cancelFutureBookings;

    @Value("${payments.past-due.grace-hours:0}")
    private long graceHours;

    /** Returns number of canceled bookings. */
    @Transactional
    public int enforcePastDue(Long userId, Long tenantId) {
        if (!cancelFutureBookings) return 0;
        Instant now    = Instant.now();
        Instant cutoff = now.plusSeconds(graceHours * 3600);
        return bookingRepository.cancelFutureActiveByUser(userId, tenantId, cutoff, now);
    }
}
