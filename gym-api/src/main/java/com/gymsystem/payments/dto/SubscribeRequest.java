package com.gymsystem.payments.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SubscribeRequest {

    @NotBlank
    private String planName;

    @Min(100)
    private long priceCents;

    @NotBlank
    private String currency;

    /** pix | boleto | credit_card */
    @NotBlank
    private String paymentMethod;

    // ── Campos para credit_card ───────────────────────────────
    private String cardToken;
    private String cardPaymentMethodId;
    private String cardIssuerId;
    private int    installments = 1;

    // ── Campos para boleto ────────────────────────────────────
    private String customerDocument;
    private String customerZipCode;
    private String customerStreet;
    private String customerStreetNum;
    private String customerNeighborhood;
    private String customerCity;
    private String customerState;
}