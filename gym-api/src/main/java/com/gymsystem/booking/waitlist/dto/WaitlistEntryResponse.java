package com.gymsystem.booking.waitlist.dto;

import java.time.Instant;

public record WaitlistEntryResponse(
    Long    entryId,
    Long    sessionId,
    String  sessionType,
    Instant sessionStartAt,
    int     position,
    String  status,
    Instant notifiedAt,
    Instant expiresAt,
    long    totalWaiting   // how many people are ahead + this user
) {}
