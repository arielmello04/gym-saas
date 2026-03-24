package com.gymsystem.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

/** Response payload representing the current booking policy. */
@Data
@AllArgsConstructor
public class BookingPolicyResponse {

    private int openDaysInAdvance;
    private Instant createdAt;
    private Instant updatedAt;
}
