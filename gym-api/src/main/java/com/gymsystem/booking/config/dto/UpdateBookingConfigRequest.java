// src/main/java/com/gymsystem/booking/config/dto/UpdateBookingConfigRequest.java
package com.gymsystem.booking.config.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/**
 * Writable fields of the booking configuration.
 *
 * The endpoint used to bind the BookingConfig entity straight from the request
 * body, which let a caller send id and tenant along with everything else.
 */
@Data
public class UpdateBookingConfigRequest {

    @NotNull
    @Min(0)
    private Integer publishDaysBeforeMonth;

    /** Weekdays the gym operates, e.g. "MON,TUE,WED,THU,FRI". */
    private String businessDays;

    private LocalTime businessStart;
    private LocalTime businessEnd;

    /** Bookings can no longer be cancelled within this many hours of the class. */
    @NotNull
    @Min(0)
    private Integer cancelCutoffHours;

    @NotNull
    private Boolean onePerDayPerType;

    @NotNull
    private Boolean waitlistEnabled;

    /** Hours a promoted member has to confirm before the spot moves on. */
    @NotNull
    @Min(1)
    private Integer waitlistPromotionHours;
}
