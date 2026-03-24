// src/main/java/com/gymsystem/booking/dto/AdminUpdatePolicyRequest.java
package com.gymsystem.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload to update the global booking policy.
 */
@Data
public class AdminUpdatePolicyRequest {

    @NotNull // Ensures the value is provided
    @Min(0)  // Allows zero to mean "book only for sessions starting today"
    @Max(365) // Arbitrary upper limit to keep values sensible
    private Integer openDaysInAdvance; // Number of days ahead users can book sessions
}
