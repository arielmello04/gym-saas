// src/main/java/com/gymsystem/booking/dto/CalendarItem.java
package com.gymsystem.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/** Calendar-friendly view with booking metadata and open/closed flag. */
@Data
@AllArgsConstructor
public class CalendarItem {
    private Long sessionId;
    private String classTypeCode;
    private String classTypeName;
    private Instant startAt;
    private Instant endAt;
    private int capacity;
    private long booked;       // number of active bookings (BOOKED)
    private long spotsLeft;    // capacity - booked (not below zero)
    private boolean canceled;  // session canceled by admin
    private boolean openForBooking; // computed using policy + monthly publish window
}
