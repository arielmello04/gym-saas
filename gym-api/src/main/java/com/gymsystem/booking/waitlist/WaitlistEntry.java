package com.gymsystem.booking.waitlist;

import com.gymsystem.booking.ClassSession;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Represents a user's position in the waitlist for a fully-booked class session.
 *
 * Flow:
 *   1. User joins waitlist → status = WAITING, position assigned
 *   2. A booking is canceled → WaitlistService.promoteNext() is called
 *   3. First WAITING entry → status = PROMOTED, notifiedAt set, expiresAt = now + promotionHours
 *   4a. User confirms within deadline → Booking is created, entry removed
 *   4b. Deadline passes → status = EXPIRED, next entry is promoted
 */
@Entity
@Table(name = "waitlist_entries")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Position in queue — 1 is first. Reassigned when entries ahead are removed. */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitlistStatus status;

    /** Timestamp when the user was notified that a spot opened up. */
    @Column(name = "notified_at")
    private Instant notifiedAt;

    /** Deadline by which the user must confirm to secure the spot. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
