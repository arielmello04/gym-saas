// src/main/java/com/gymsystem/payments/dto/PaymentItem.java
package com.gymsystem.payments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class PaymentItem {
    private Long id;
    private long amountCents;
    private String currency;
    private String status;
    private String providerRef;
    private Instant dueAt;
    private Instant paidAt;
    private Instant createdAt;
}
