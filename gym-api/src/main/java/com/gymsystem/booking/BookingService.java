package com.gymsystem.booking;

import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.booking.dto.AdminCreateSessionRequest;
import com.gymsystem.booking.dto.AvailabilityItem;
import com.gymsystem.booking.dto.BookingResponse;
import com.gymsystem.booking.waitlist.WaitlistService;
import com.gymsystem.common.ratelimit.RateLimiter;
import com.gymsystem.i18n.I18n;
import com.gymsystem.payments.SubscriptionRepository;
import com.gymsystem.payments.SubscriptionStatus;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ClassTypeRepository     classTypeRepository;
    private final ClassSessionRepository  classSessionRepository;
    private final BookingRepository       bookingRepository;
    private final UserRepository          userRepository;
    private final BookingPolicyRepository policyRepository;
    private final SubscriptionRepository  subscriptionRepository;
    private final BookingConfigService    bookingConfigService;
    private final TenantRepository        tenantRepository;
    private final I18n                    i18n;
    private final RateLimiter             rateLimiter;

    // @Lazy to break circular dependency (WaitlistService → BookingService → WaitlistService)
    @Lazy
    private final WaitlistService waitlistService;

    @Value("${ratelimit.bookings.book-min-interval-ms:800}")
    private long bookMinIntervalMs;

    @Value("${ratelimit.bookings.cancel-min-interval-ms:800}")
    private long cancelMinIntervalMs;

    // ── Admin ─────────────────────────────────────────────────

    @Transactional
    public Long createSession(AdminCreateSessionRequest request) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        var  admin    = getCurrentUser();

        var classType = classTypeRepository
                .findByCodeAndActiveTrueAndTenantId(request.getClassTypeCode(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown or inactive class type: " + request.getClassTypeCode()));

        if (!request.getStartAt().isBefore(request.getEndAt()))
            throw new IllegalArgumentException("startAt must be before endAt");
        if (request.getCapacity() <= 0)
            throw new IllegalArgumentException("capacity must be > 0");

        Instant now = Instant.now();
        if (request.getEndAt().isBefore(now))
            throw new IllegalArgumentException("Cannot create sessions in the past");

        var session = ClassSession.builder()
                .classType(classType).tenant(tenant)
                .startAt(request.getStartAt()).endAt(request.getEndAt())
                .capacity(request.getCapacity()).canceled(false)
                .notes(request.getNotes()).createdByAdminId(admin.getId())
                .createdAt(now).build();

        return classSessionRepository.save(session).getId();
    }

    @Transactional
    public void cancelSession(Long sessionId) {
        Long tenantId = TenantGuard.currentTenantId();
        getCurrentUser();

        var session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        TenantGuard.requireMatch(session.getTenant());

        long active = bookingRepository.countActiveBySessionId(sessionId, tenantId);
        if (active > 0)
            throw new IllegalStateException("Cannot cancel a session with active bookings");

        session.setCanceled(true);
        classSessionRepository.save(session);
    }

    // ── Availability ──────────────────────────────────────────

    public List<AvailabilityItem> getAvailability(Instant from, Instant to) {
        if (from == null || to == null) throw new IllegalArgumentException("from and to are required");
        if (!from.isBefore(to))        throw new IllegalArgumentException("from must be before to");

        Long    tenantId     = TenantGuard.currentTenantId();
        Instant now          = Instant.now();
        var     policy       = policyRepository.findTopByTenantIdOrderByIdAsc(tenantId).orElse(null);
        Instant policyUpper  = (policy == null)
                ? Instant.MAX
                : now.plus(policy.getOpenDaysInAdvance(), ChronoUnit.DAYS);

        Instant effectiveTo  = to.isBefore(policyUpper) ? to : policyUpper;
        if (!from.isBefore(effectiveTo)) return List.of();

        var sessions = classSessionRepository.findActiveSessionsBetween(tenantId, from, effectiveTo);
        var cfg      = bookingConfigService.get();
        var nowZ     = ZonedDateTime.now(ZoneOffset.UTC);

        List<AvailabilityItem> items = new ArrayList<>();
        for (var s : sessions) {
            var z        = s.getStartAt().atZone(ZoneOffset.UTC);
            var firstDay = z.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            var openAt   = firstDay.minusDays(cfg.getPublishDaysBeforeMonth());
            if (nowZ.isBefore(openAt)) continue;

            long booked    = bookingRepository.countActiveBySessionId(s.getId(), tenantId);
            long spotsLeft = Math.max(0, s.getCapacity() - booked);

            items.add(new AvailabilityItem(
                    s.getId(), s.getClassType().getCode(), s.getClassType().getName(),
                    s.getStartAt(), s.getEndAt(), s.getCapacity(), spotsLeft, s.getNotes()));
        }
        return items;
    }

    // ── Booking ───────────────────────────────────────────────

    @Transactional
    public BookingResponse bookSession(Long sessionId) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        var  user     = getCurrentUser();

        rateLimiter.enforceMinInterval("book:" + user.getId(), bookMinIntervalMs,
                "Too many booking attempts; please wait a moment");

        assertUserHasActiveSubscription(user.getId(), tenantId);

        var session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        TenantGuard.requireMatch(session.getTenant());

        Instant now = Instant.now();
        if (session.isCanceled())                throw new IllegalStateException(i18n.msg("booking.session.canceled"));
        if (!now.isBefore(session.getStartAt())) throw new IllegalStateException(i18n.msg("booking.already.started"));

        var policy = policyRepository.findTopByTenantIdOrderByIdAsc(tenantId).orElse(null);
        if (policy != null) {
            Instant horizon = now.plus(policy.getOpenDaysInAdvance(), ChronoUnit.DAYS);
            if (session.getStartAt().isAfter(horizon))
                throw new IllegalStateException(i18n.msg("booking.horizon.exceeded"));
        }
        assertBookingWindowAllows(session.getStartAt());

        var cfg = bookingConfigService.get();
        if (cfg.isOnePerDayPerType()) {
            var z        = session.getStartAt().atZone(ZoneOffset.UTC);
            var dayStart = z.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            var dayEnd   = z.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            long already = bookingRepository.countActiveForUserByTypeAndDay(
                    user.getId(), tenantId, session.getClassType().getId(), dayStart, dayEnd);
            if (already > 0) throw new IllegalStateException(i18n.msg("booking.oneperday"));
        }

        if (bookingRepository.findActiveBySessionIdAndUserId(sessionId, user.getId(), tenantId).isPresent())
            throw new IllegalStateException(i18n.msg("booking.duplicate.session"));

        long activeCount = bookingRepository.countActiveBySessionId(sessionId, tenantId);
        if (activeCount >= session.getCapacity())
            throw new IllegalStateException(i18n.msg("booking.full"));

        var booking = Booking.builder()
                .session(session).user(user).tenant(tenant)
                .status(BookingStatus.BOOKED).createdAt(now)
                .build();
        var saved = bookingRepository.save(booking);

        return new BookingResponse(saved.getId(), sessionId, saved.getStatus().name(),
                saved.getCreatedAt(), saved.getCanceledAt());
    }

    /**
     * Cancels a booking and automatically promotes the next waitlisted user if one exists.
     */
    @Transactional
    public void cancelMyBooking(Long bookingId) {
        Long tenantId = TenantGuard.currentTenantId();
        var  user     = getCurrentUser();

        rateLimiter.enforceMinInterval("cancel:" + user.getId(), cancelMinIntervalMs,
                "Too many cancel attempts; please wait a moment");

        var booking = bookingRepository
                .findByIdAndUserId(bookingId, user.getId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Booking not found for the current user: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELED) return;

        var     cfg          = bookingConfigService.get();
        int     cutoff       = Math.max(0, cfg.getCancelCutoffHours());
        Instant latestAllowed = booking.getSession().getStartAt().minusSeconds(cutoff * 3600L);
        if (!Instant.now().isBefore(latestAllowed))
            throw new IllegalStateException(i18n.msg("booking.cancel.cutoff"));

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.CANCELED);
        booking.setCanceledAt(now);
        bookingRepository.save(booking);

        // ── Trigger waitlist promotion ───────────────────────
        waitlistService.promoteNext(booking.getSession().getId(), tenantId);
    }

    // ── Helpers ───────────────────────────────────────────────

    private User getCurrentUser() {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private void assertUserHasActiveSubscription(Long userId, Long tenantId) {
        if (subscriptionRepository.findByUserIdAndTenantIdAndStatusIn(
                userId, tenantId, Set.of(SubscriptionStatus.ACTIVE)).isEmpty()) {
            throw new IllegalStateException("User does not have an active subscription");
        }
    }

    private void assertBookingWindowAllows(Instant sessionStart) {
        var cfg      = bookingConfigService.get();
        var zSession = sessionStart.atZone(ZoneOffset.UTC);
        var firstDay = zSession.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        var openAt   = firstDay.minusDays(cfg.getPublishDaysBeforeMonth());
        if (ZonedDateTime.now(ZoneOffset.UTC).isBefore(openAt))
            throw new IllegalStateException(i18n.msg("booking.month.not.open"));
    }
}
