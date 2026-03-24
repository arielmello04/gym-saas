package com.gymsystem.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

/** A flat, client-friendly view of an available class session. */
@Data
@AllArgsConstructor
public class AvailabilityItem {

    private Long sessionId;
    private String classTypeCode;
    private String classTypeName;
    private Instant startAt;
    private Instant endAt;
    private int capacity;
    private long spotsLeft;
    private String notes;
}
