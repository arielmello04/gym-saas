package com.gymsystem.payments.gateway;

import lombok.Builder;
import lombok.Getter;

/** Input for creating a charge at the payment provider. */
@Getter
@Builder
public class GatewayChargeRequest {

    /** Unique idempotency key — prevents duplicate charges on retry. */
    private String idempotencyKey;

    /** Amount in smallest currency unit (centavos / cents). */
    private long amountCents;

    /** ISO 4217 currency code (e.g. "BRL", "USD"). */
    private String currency;

    /** Customer's email — used by providers for identification / receipts. */
    private String customerEmail;

    /** Customer's name. */
    private String customerName;

    /** Optional CPF/document for Brazilian gateways (Mercado Pago, Pagar.me). */
    private String customerDocument;

    /** Payment method: "pix", "credit_card", "boleto". */
    private String paymentMethod;

    /** Human-readable description that appears on the customer's bill. */
    private String description;

    /** Webhook URL where the provider should POST status updates. */
    private String callbackUrl;
}
