package com.gymsystem.booking.waitlist;

import com.gymsystem.booking.Booking;
import com.gymsystem.booking.BookingRepository;
import com.gymsystem.booking.BookingStatus;
import com.gymsystem.booking.ClassSessionRepository;
import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.booking.waitlist.dto.WaitlistEntryResponse;
import com.gymsystem.notifications.EmailService;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Manages the waitlist lifecycle:
 *
 *  join()       — user joins the queue for a full session
 *  confirm()    — user confirms the promoted spot (creates booking)
 *  leave()      — user leaves the queue voluntarily
 *  promoteNext()— called automatically when a booking is canceled
 *  expireStale()— scheduled job that expires unconfirmed PROMOTED entries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository      waitlistRepository;
    private final ClassSessionRepository  sessionRepository;
    private final BookingRepository       bookingRepository;
    private final UserRepository          userRepository;
    private final TenantRepository        tenantRepository;
    private final BookingConfigService    configService;
    private final EmailService            emailService;

    // ── User-facing operations ────────────────────────────────

    /**
     * Adds the current user to the waitlist for a full session.
     * Validates that the session is actually full before joining.
     */
    @Transactional
    public WaitlistEntryResponse join(Long sessionId) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        var  cfg      = configService.get();
        User user     = currentUser();

        if (!cfg.isWaitlistEnabled()) {
            throw new IllegalStateException("Waitlist is not enabled for this gym");
        }

        var session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!session.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("Session does not belong to this tenant");
        }
        if (session.isCanceled()) {
            throw new IllegalStateException("Cannot join waitlist for a canceled session");
        }

        // Guard: session must actually be full
        long booked = bookingRepository.countActiveBySessionId(sessionId, tenantId);
        if (booked < session.getCapacity()) {
            throw new IllegalStateException(
                    "Session still has " + (session.getCapacity() - booked) + " spot(s). Book directly.");
        }

        // Guard: user must not already be in waitlist or have an active booking
        boolean alreadyWaiting = waitlistRepository.existsBySessionIdAndUserIdAndStatusIn(
                sessionId, user.getId(), List.of(WaitlistStatus.WAITING, WaitlistStatus.PROMOTED));
        if (alreadyWaiting) {
            throw new IllegalStateException("You are already in the waitlist for this session");
        }

        boolean alreadyBooked = bookingRepository
                .findActiveBySessionIdAndUserId(sessionId, user.getId(), tenantId).isPresent();
        if (alreadyBooked) {
            throw new IllegalStateException("You already have an active booking for this session");
        }

        int nextPosition = waitlistRepository.findMaxPosition(sessionId) + 1;
        Instant now = Instant.now();

        WaitlistEntry entry = WaitlistEntry.builder()
                .tenant(tenant)
                .session(session)
                .user(user)
                .position(nextPosition)
                .status(WaitlistStatus.WAITING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        entry = waitlistRepository.save(entry);
        log.info("[Waitlist] User {} joined waitlist for session {} at position {}",
                user.getEmail(), sessionId, nextPosition);

        return toResponse(entry);
    }

    /**
     * Confirms the promoted spot, creating an actual booking for the user.
     * Must be called before expiresAt.
     */
    @Transactional
    public Booking confirm(Long sessionId) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        User user     = currentUser();

        WaitlistEntry entry = waitlistRepository
                .findBySessionIdAndUserIdAndStatus(sessionId, user.getId(), WaitlistStatus.PROMOTED)
                .orElseThrow(() -> new IllegalStateException(
                        "No promoted waitlist entry found for this session"));

        if (Instant.now().isAfter(entry.getExpiresAt())) {
            throw new IllegalStateException(
                    "Your promotion has expired. You have been removed from the queue.");
        }

        // Double-check capacity (edge case: another confirm raced ahead)
        long booked = bookingRepository.countActiveBySessionId(sessionId, tenantId);
        if (booked >= entry.getSession().getCapacity()) {
            // Expire this entry and promote next
            entry.setStatus(WaitlistStatus.EXPIRED);
            entry.setUpdatedAt(Instant.now());
            waitlistRepository.save(entry);
            promoteNext(sessionId, tenantId);
            throw new IllegalStateException("Spot was taken by someone else. You have been re-queued or removed.");
        }

        // Create the booking
        Instant now = Instant.now();
        Booking booking = Booking.builder()
                .session(entry.getSession())
                .user(user)
                .tenant(tenant)
                .status(BookingStatus.BOOKED)
                .createdAt(now)
                .build();
        booking = bookingRepository.save(booking);

        // Remove waitlist entry (no longer needed)
        waitlistRepository.delete(entry);

        log.info("[Waitlist] User {} confirmed spot for session {} — booking {} created",
                user.getEmail(), sessionId, booking.getId());

        return booking;
    }

    /**
     * User voluntarily leaves the waitlist.
     * Shifts positions of entries behind and does NOT trigger promotion
     * (promotion only happens when a real booking is canceled).
     */
    @Transactional
    public void leave(Long sessionId) {
        Long tenantId = TenantGuard.currentTenantId();
        User user     = currentUser();

        WaitlistEntry entry = waitlistRepository
                .findBySessionIdAndUserIdAndStatus(sessionId, user.getId(), WaitlistStatus.WAITING)
                .orElseGet(() -> waitlistRepository
                        .findBySessionIdAndUserIdAndStatus(sessionId, user.getId(), WaitlistStatus.PROMOTED)
                        .orElseThrow(() -> new IllegalStateException("No active waitlist entry found")));

        int pos = entry.getPosition();
        entry.setStatus(WaitlistStatus.CANCELED);
        entry.setUpdatedAt(Instant.now());
        waitlistRepository.save(entry);

        // Compact positions behind the removed entry
        waitlistRepository.shiftPositionsDown(sessionId, pos, Instant.now());

        log.info("[Waitlist] User {} left waitlist for session {}", user.getEmail(), sessionId);
    }

    /** Returns all active waitlist entries for the current user in this tenant. */
    public List<WaitlistEntryResponse> myEntries() {
        Long tenantId = TenantGuard.currentTenantId();
        return waitlistRepository.findActiveByUser(currentUser().getId(), tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Internal / scheduled ─────────────────────────────────

    /**
     * Promotes the next WAITING user when a booking spot opens.
     * Called by BookingService after a booking is canceled.
     */
    @Transactional
    public void promoteNext(Long sessionId, Long tenantId) {
        var cfg = configService.get();
        if (!cfg.isWaitlistEnabled()) return;

        waitlistRepository.findNextWaiting(sessionId, tenantId).ifPresent(entry -> {
            Instant now = Instant.now();
            entry.setStatus(WaitlistStatus.PROMOTED);
            entry.setNotifiedAt(now);
            entry.setExpiresAt(now.plusSeconds(cfg.getWaitlistPromotionHours() * 3600L));
            entry.setUpdatedAt(now);
            waitlistRepository.save(entry);

            // Notify user by email
            emailService.sendWaitlistPromotion(
                    entry.getUser(),
                    entry.getSession(),
                    entry.getExpiresAt()
            );

            log.info("[Waitlist] Promoted user {} for session {} — confirm by {}",
                    entry.getUser().getEmail(), sessionId, entry.getExpiresAt());
        });
    }

    /**
     * Scheduled job: expires PROMOTED entries whose deadline has passed,
     * then promotes the next person in queue.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${waitlist.expiry-check-ms:300000}")
    @Transactional
    public void expireStalePromotions() {
        List<WaitlistEntry> stale = waitlistRepository.findExpiredPromotions(Instant.now());
        if (stale.isEmpty()) return;

        log.info("[Waitlist] Expiring {} stale promotion(s)", stale.size());
        Instant now = Instant.now();

        for (WaitlistEntry entry : stale) {
            entry.setStatus(WaitlistStatus.EXPIRED);
            entry.setUpdatedAt(now);
            waitlistRepository.save(entry);

            // Notify user their window closed
            emailService.sendWaitlistExpired(entry.getUser(), entry.getSession());

            // Promote the next person
            promoteNext(entry.getSession().getId(), entry.getTenant().getId());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private WaitlistEntryResponse toResponse(WaitlistEntry w) {
        long total = waitlistRepository.countWaiting(w.getSession().getId());
        return new WaitlistEntryResponse(
                w.getId(),
                w.getSession().getId(),
                w.getSession().getClassType().getCode(),
                w.getSession().getStartAt(),
                w.getPosition(),
                w.getStatus().name(),
                w.getNotifiedAt(),
                w.getExpiresAt(),
                total
        );
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
