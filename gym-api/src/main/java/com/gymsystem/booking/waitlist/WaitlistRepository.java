package com.gymsystem.booking.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    /** Checks if a user is already in the waitlist for a session (any active status). */
    boolean existsBySessionIdAndUserIdAndStatusIn(
            Long sessionId, Long userId, List<WaitlistStatus> statuses);

    /** Finds a specific user's active waitlist entry for a session. */
    Optional<WaitlistEntry> findBySessionIdAndUserIdAndStatus(
            Long sessionId, Long userId, WaitlistStatus status);

    /** Returns all WAITING entries for a session ordered by position (for promotion). */
    @Query("""
        SELECT w FROM WaitlistEntry w
        WHERE w.session.id = :sessionId
          AND w.tenant.id  = :tenantId
          AND w.status = com.gymsystem.booking.waitlist.WaitlistStatus.WAITING
        ORDER BY w.position ASC
    """)
    List<WaitlistEntry> findWaitingBySessionOrdered(
            @Param("sessionId") Long sessionId,
            @Param("tenantId")  Long tenantId);

    /** Returns the next entry to be promoted (first WAITING position). */
    @Query("""
        SELECT w FROM WaitlistEntry w
        WHERE w.session.id = :sessionId
          AND w.tenant.id  = :tenantId
          AND w.status = com.gymsystem.booking.waitlist.WaitlistStatus.WAITING
        ORDER BY w.position ASC
        LIMIT 1
    """)
    Optional<WaitlistEntry> findNextWaiting(
            @Param("sessionId") Long sessionId,
            @Param("tenantId")  Long tenantId);

    /** Returns all PROMOTED entries whose deadline has passed (for expiry job). */
    @Query("""
        SELECT w FROM WaitlistEntry w
        WHERE w.status = com.gymsystem.booking.waitlist.WaitlistStatus.PROMOTED
          AND w.expiresAt <= :now
    """)
    List<WaitlistEntry> findExpiredPromotions(@Param("now") Instant now);

    /** All active waitlist entries for a user across a tenant. */
    @Query("""
        SELECT w FROM WaitlistEntry w
        WHERE w.user.id   = :userId
          AND w.tenant.id = :tenantId
          AND w.status IN (
              com.gymsystem.booking.waitlist.WaitlistStatus.WAITING,
              com.gymsystem.booking.waitlist.WaitlistStatus.PROMOTED)
        ORDER BY w.createdAt DESC
    """)
    List<WaitlistEntry> findActiveByUser(
            @Param("userId")   Long userId,
            @Param("tenantId") Long tenantId);

    /** Current waitlist size for a session (WAITING only). */
    @Query("""
        SELECT COUNT(w) FROM WaitlistEntry w
        WHERE w.session.id = :sessionId
          AND w.status = com.gymsystem.booking.waitlist.WaitlistStatus.WAITING
    """)
    long countWaiting(@Param("sessionId") Long sessionId);

    /** Shifts positions down after an entry is removed from the queue. */
    @Modifying
    @Query("""
        UPDATE WaitlistEntry w
           SET w.position = w.position - 1,
               w.updatedAt = :now
         WHERE w.session.id = :sessionId
           AND w.status = com.gymsystem.booking.waitlist.WaitlistStatus.WAITING
           AND w.position > :afterPosition
    """)
    int shiftPositionsDown(
            @Param("sessionId")      Long sessionId,
            @Param("afterPosition")  int afterPosition,
            @Param("now")            Instant now);

    /** Max position currently in WAITING state for a session. */
    @Query("""
        SELECT COALESCE(MAX(w.position), 0) FROM WaitlistEntry w
        WHERE w.session.id = :sessionId
          AND w.status = com.gymsystem.booking.waitlist.WaitlistStatus.WAITING
    """)
    int findMaxPosition(@Param("sessionId") Long sessionId);
}
