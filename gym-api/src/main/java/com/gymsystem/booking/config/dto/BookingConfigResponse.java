// src/main/java/com/gymsystem/booking/config/dto/BookingConfigResponse.java
package com.gymsystem.booking.config.dto;

import com.gymsystem.booking.config.BookingConfig;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Booking configuration as the API exposes it — without the Tenant association
 * the entity carries.
 */
@Data
@AllArgsConstructor
public class BookingConfigResponse {

    private int       publishDaysBeforeMonth;
    private String    businessDays;
    private LocalTime businessStart;
    private LocalTime businessEnd;
    private int       cancelCutoffHours;
    private boolean   onePerDayPerType;
    private boolean   waitlistEnabled;
    private int       waitlistPromotionHours;
    private Instant   updatedAt;

    public static BookingConfigResponse from(BookingConfig c) {
        return new BookingConfigResponse(
                c.getPublishDaysBeforeMonth(),
                c.getBusinessDays(),
                c.getBusinessStart(),
                c.getBusinessEnd(),
                c.getCancelCutoffHours(),
                c.isOnePerDayPerType(),
                c.isWaitlistEnabled(),
                c.getWaitlistPromotionHours(),
                c.getUpdatedAt()
        );
    }
}
