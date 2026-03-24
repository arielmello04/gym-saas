package com.gymsystem.payments.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mock gateway for local development and testing.
 * Active when payments.gateway=mock (default).
 * Always approves charges immediately.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest request) {
        String ref = "MOCK-" + UUID.randomUUID();
        log.info("[MockGateway] Charge created: ref={} amount={} {}",
                ref, request.getAmountCents(), request.getCurrency());
        return GatewayChargeResponse.builder()
                .providerRef(ref)
                .status(GatewayPaymentStatus.PAID)
                .rawResponse("{\"mock\":true}")
                .build();
    }

    @Override
    public GatewayStatusResponse status(String providerRef) {
        return GatewayStatusResponse.builder()
                .providerRef(providerRef)
                .status(GatewayPaymentStatus.PAID)
                .paidAt(Instant.now())
                .build();
    }

    @Override
    public void refund(String providerRef, Long amountCents) {
        log.info("[MockGateway] Refund: ref={} amount={}", providerRef, amountCents);
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, byte[] rawBody) {
        return true; // Always valid in mock
    }
}
