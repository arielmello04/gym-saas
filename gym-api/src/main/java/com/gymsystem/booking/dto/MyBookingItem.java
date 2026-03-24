// src/main/java/com/gymsystem/booking/dto/MyBookingItem.java
package com.gymsystem.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * Lightweight booking row for the user's "My bookings" page.
 * Keeps only the fields the UI needs to render a list and decide actions.
 */
@Data
@AllArgsConstructor
public class MyBookingItem {
    private Long bookingId;
    private Long sessionId;
    private String classTypeCode;
    private String classTypeName;
    private Instant startAt;
    private Instant endAt;
    private String status;      // BOOKED | CANCELED
    private boolean cancellable; // true if now < startAt (no extra cancellation window enforced yet)
}
