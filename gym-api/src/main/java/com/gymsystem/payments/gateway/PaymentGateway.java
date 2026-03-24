package com.gymsystem.payments.gateway;

import java.util.Map;

/**
 * Provider-agnostic payment gateway contract.
 *
 * Implementations:
 *   - MockPaymentGateway    (dev/test)
 *   - MercadoPagoGateway    (production — plug when ready)
 *   - PagarmeGateway        (production — plug when ready)
 *   - StripeGateway         (production — plug when ready)
 *
 * Activation: set spring.profiles.active=mercadopago (or pagarme, stripe)
 */
public interface PaymentGateway {

    /**
     * Creates a charge for the given amount.
     *
     * @param request  Charge parameters (amount, currency, customer, method)
     * @return         Gateway response with providerRef and payment URL (if async)
     */
    GatewayChargeResponse charge(GatewayChargeRequest request);

    /**
     * Queries the current status of a charge at the provider.
     *
     * @param providerRef  The reference returned by charge()
     * @return             Current status from the provider
     */
    GatewayStatusResponse status(String providerRef);

    /**
     * Refunds a previously paid charge (full or partial).
     *
     * @param providerRef   The reference returned by charge()
     * @param amountCents   Amount to refund; null = full refund
     */
    void refund(String providerRef, Long amountCents);

    /**
     * Verifies the HMAC / signature of an incoming webhook payload.
     * Prevents spoofed callbacks.
     *
     * @param headers   HTTP headers from the webhook request
     * @param rawBody   Raw request body bytes
     * @return          true if signature is valid
     */
    boolean verifyWebhook(Map<String, String> headers, byte[] rawBody);
}
