package com.gymsystem.payments.gateway;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

/** Normalized status query response. */
@Getter
@Builder
public class GatewayStatusResponse {
    private String               providerRef;
    private GatewayPaymentStatus status;
    private Instant              paidAt;
    private String               failureReason;
}
