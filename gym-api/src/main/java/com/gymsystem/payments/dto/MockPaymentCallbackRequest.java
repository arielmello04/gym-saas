// src/main/java/com/gymsystem/payments/dto/MockPaymentCallbackRequest.java
package com.gymsystem.payments.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Fake provider callback for MVP: approve or fail a specific payment id. */
@Data
public class MockPaymentCallbackRequest {
    @NotNull
    private Long paymentId;
    @NotNull
    private Boolean approved; // true -> PAID, false -> FAILED
}
