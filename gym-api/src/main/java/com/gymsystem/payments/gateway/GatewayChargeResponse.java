package com.gymsystem.payments.gateway;

import lombok.Builder;
import lombok.Getter;

/** Response from the provider after a charge is initiated. */
@Getter
@Builder
public class GatewayChargeResponse {

    /** Provider's unique reference for this charge (store in Payment.providerRef). */
    private String providerRef;

    /** Current status at creation time: PENDING, PAID, FAILED. */
    private GatewayPaymentStatus status;

    /**
     * For async methods (Pix, Boleto): URL or code the customer uses to pay.
     * Null for synchronous card charges that are approved immediately.
     */
    private String paymentUrl;

    /** Pix QR code (base64 image) — only for Pix payments. */
    private String pixQrCode;

    /** Pix copy-paste code — only for Pix payments. */
    private String pixCode;

    /** Boleto barcode — only for Boleto payments. */
    private String boletoBarcode;

    /** Raw provider response for debugging / audit. */
    private String rawResponse;
}
