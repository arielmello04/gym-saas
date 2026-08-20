package com.gymsystem.payments.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de assinatura.
 *
 * Só o plano e a forma de pagamento vêm do cliente. Nome, preço e moeda saem do
 * catálogo da academia (membership_plans): antes esses três campos chegavam no
 * corpo da requisição e eram cobrados como vieram, o que permitia assinar
 * qualquer plano por R$ 1,00.
 */
@Getter @Setter
public class SubscribeRequest {

    @NotNull
    private Long planId;

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
