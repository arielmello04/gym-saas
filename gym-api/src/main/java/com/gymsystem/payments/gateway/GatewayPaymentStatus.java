package com.gymsystem.payments.gateway;

/** Normalized payment status — same across all gateway providers. */
public enum GatewayPaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
    CANCELED
}
