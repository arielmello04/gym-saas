package com.gymsystem.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de reserva.
 *
 * TODAS recebem tenantId e filtram por b.tenant.id. Nao adicione sobrecarga sem
 * tenant: ja existiu uma e o /api/v1/my/bookings acabou listando as reservas de
 * todas as academias do usuario.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.session.id = :sessionId
          AND b.tenant.id  = :tenantId
          AND b.status     = com.gymsystem.booking.BookingStatus.BOOKED
    """)
    long countActiveBySessionId(@Param("sessionId") Long sessionId,
                                @Param("tenantId")  Long tenantId);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.session.id  = :sessionId
          AND b.user.id     = :userId
          AND b.tenant.id   = :tenantId
          AND b.status      = com.gymsystem.booking.BookingStatus.BOOKED
    """)
    Optional<Booking> findActiveBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId")    Long userId,
            @Param("tenantId")  Long tenantId);

    @Query("""
        SELECT b FROM Booking b
        WHERE b.id          = :bookingId
          AND b.user.id     = :userId
          AND b.tenant.id   = :tenantId
    """)
    Optional<Booking> findByIdAndUserId(
            @Param("bookingId") Long bookingId,
            @Param("userId")    Long userId,
            @Param("tenantId")  Long tenantId);

    @Query("""
        SELECT COUNT(b) FROM Booking b
        WHERE b.user.id                = :userId
          AND b.tenant.id              = :tenantId
          AND b.session.classType.id   = :classTypeId
          AND b.session.startAt       >= :dayStart
          AND b.session.startAt        < :dayEnd
          AND b.status                 = com.gymsystem.booking.BookingStatus.BOOKED
    """)
    long countActiveForUserByTypeAndDay(
            @Param("userId")      Long userId,
            @Param("tenantId")    Long tenantId,
            @Param("classTypeId") Long classTypeId,
            @Param("dayStart")    Instant dayStart,
            @Param("dayEnd")      Instant dayEnd);

    // ── Usado por BookingService e MyBookingsController ──────

    @Query("""
        SELECT b FROM Booking b
          JOIN FETCH b.session s
          JOIN FETCH s.classType
        WHERE b.user.id   = :userId
          AND b.tenant.id = :tenantId
        ORDER BY s.startAt DESC
    """)
    List<Booking> findAllByUserIdWithSession(@Param("userId")   Long userId,
                                             @Param("tenantId") Long tenantId);

    @Modifying
    @Query("""
    UPDATE Booking b
    SET b.status = com.gymsystem.booking.BookingStatus.CANCELED,
        b.canceledAt = :canceledAt
    WHERE b.user.id   = :userId
      AND b.tenant.id = :tenantId
      AND b.session.startAt > :from
      AND b.status    = com.gymsystem.booking.BookingStatus.BOOKED
""")
    int cancelFutureActiveByUser(
            @Param("userId")     Long userId,
            @Param("tenantId")   Long tenantId,
            @Param("from")       Instant from,
            @Param("canceledAt") Instant canceledAt);
}
