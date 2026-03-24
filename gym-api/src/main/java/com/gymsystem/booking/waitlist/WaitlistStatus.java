package com.gymsystem.booking.waitlist;

public enum WaitlistStatus {
    /** Waiting for a spot to open up. */
    WAITING,
    /** Spot available — user was notified and has a deadline to confirm. */
    PROMOTED,
    /** Confirmation deadline passed without a response; next in queue was called. */
    EXPIRED,
    /** User voluntarily left the queue. */
    CANCELED
}
