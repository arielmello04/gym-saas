// src/main/java/com/gymsystem/booking/dto/AdminCreateSessionRequest.java
package com.gymsystem.booking.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.Instant;

/**
 * Payload used by admin to create a class session.
 */
@Data
public class AdminCreateSessionRequest {

    @NotBlank
    @Size(max = 64)
    private String classTypeCode; // Code of the class type (e.g., "PILATES")

    @NotNull
    private Instant startAt; // ISO-8601 timestamp for the session start (UTC)

    @NotNull
    private Instant endAt; // ISO-8601 timestamp for the session end (UTC)

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer capacity; // Maximum number of allowed bookings

    @Size(max = 255) // Optional short note with a length bound
    private String notes; // Optional notes about the session
}
