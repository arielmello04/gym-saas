// src/main/java/com/gymsystem/payments/dto/SubscriptionResponse.java
package com.gymsystem.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class SubscriptionResponse {
    private Long id;
    private String planName;
    private long priceCents;
    private String currency;
    private int billingDay;
    private String status;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant nextBillingAt;
}
