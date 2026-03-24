package com.gymsystem.booking.dto;

import java.time.Instant;

public record BookingResponse(
    Long    id,
    Long    sessionId,
    String  status,
    Instant createdAt,
    Instant canceledAt
) {}
