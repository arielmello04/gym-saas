package com.gymsystem.payments.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsystem.payments.webhook.HmacVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Stripe gateway implementation.
 *
 * Activate with:   payments.gateway=stripe
 * Required config: payments.stripe.secret-key
 *                  payments.stripe.webhook-secret
 *
 * Supports: Card, PIX (via Stripe), Boleto (via Stripe)
 * Docs: https://stripe.com/docs/api
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "stripe")
@RequiredArgsConstructor
public class StripeGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.stripe.com/v1";

    @Value("${payments.stripe.secret-key}")
    private String secretKey;

    @Value("${payments.stripe.webhook-secret}")
    private String webhookSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest req) {
        try {
            var params = new LinkedMultiValueMap<String, String>();
            params.add("amount",      String.valueOf(req.getAmountCents()));
            params.add("currency",    req.getCurrency().toLowerCase());
            params.add("description", req.getDescription() != null ? req.getDescription() : "GymSystem");
            params.add("metadata[idempotency_key]", req.getIdempotencyKey());
            params.add("receipt_email", req.getCustomerEmail());

            String method = req.getPaymentMethod();
            if ("pix".equalsIgnoreCase(method)) {
                params.add("payment_method_types[]", "pix");
            } else if ("boleto".equalsIgnoreCase(method)) {
                params.add("payment_method_types[]", "boleto");
                params.add("payment_method_data[type]", "boleto");
                params.add("payment_method_data[boleto][tax_id]",
                        req.getCustomerDocument() != null ? req.getCustomerDocument() : "");
            } else {
                params.add("payment_method_types[]", "card");
            }

            if (req.getCallbackUrl() != null) {
                params.add("return_url", req.getCallbackUrl());
            }

            var response = restTemplate.exchange(
                    BASE_URL + "/payment_intents",
                    HttpMethod.POST,
                    new HttpEntity<>(params, buildHeaders(req.getIdempotencyKey())),
                    Map.class
            );

            var data     = response.getBody();
            String piId  = (String) data.get("id");
            String status = (String) data.get("status");

            String pixCode = null, boletoBarcode = null;
            var nextAction = (Map<?, ?>) data.get("next_action");
            if (nextAction != null) {
                var pixDisplay = (Map<?, ?>) nextAction.get("pix_display_qr_code");
                if (pixDisplay != null) pixCode = (String) pixDisplay.get("data");
                var boleto = (Map<?, ?>) nextAction.get("boleto_display_details");
                if (boleto != null) boletoBarcode = (String) boleto.get("number");
            }

            return GatewayChargeResponse.builder()
                    .providerRef(piId)
                    .status(mapStatus(status))
                    .pixCode(pixCode)
                    .boletoBarcode(boletoBarcode)
                    .rawResponse(objectMapper.writeValueAsString(data))
                    .build();

        } catch (Exception e) {
            log.error("[Stripe] Charge failed: {}", e.getMessage(), e);
            throw new RuntimeException("Stripe charge failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayStatusResponse status(String providerRef) {
        try {
            var response = restTemplate.exchange(
                    BASE_URL + "/payment_intents/" + providerRef,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(null)),
                    Map.class
            );
            var data   = response.getBody();
            String st  = (String) data.get("status");
            Long paidAt = st.equals("succeeded") ? (Long) data.get("created") : null;
            return GatewayStatusResponse.builder()
                    .providerRef(providerRef)
                    .status(mapStatus(st))
                    .paidAt(paidAt != null ? Instant.ofEpochSecond(paidAt) : null)
                    .build();
        } catch (Exception e) {
            log.error("[Stripe] Status check failed: {}", e.getMessage());
            throw new RuntimeException("Stripe status check failed", e);
        }
    }

    @Override
    public void refund(String providerRef, Long amountCents) {
        try {
            var params = new LinkedMultiValueMap<String, String>();
            params.add("payment_intent", providerRef);
            if (amountCents != null) params.add("amount", String.valueOf(amountCents));

            restTemplate.exchange(
                    BASE_URL + "/refunds",
                    HttpMethod.POST,
                    new HttpEntity<>(params, buildHeaders(null)),
                    Map.class
            );
            log.info("[Stripe] Refund OK for ref={}", providerRef);
        } catch (Exception e) {
            log.error("[Stripe] Refund failed: {}", e.getMessage());
            throw new RuntimeException("Stripe refund failed", e);
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, byte[] rawBody) {
        try {
            // Stripe-Signature: t=timestamp,v1=signature
            String stripeSignature = headers.get("stripe-signature");
            if (stripeSignature == null) return false;

            String timestamp = null, v1 = null;
            for (String part : stripeSignature.split(",")) {
                if (part.startsWith("t="))  timestamp = part.substring(2);
                if (part.startsWith("v1=")) v1 = part.substring(3);
            }
            if (timestamp == null || v1 == null) return false;

            String signedPayload = timestamp + "." + new String(rawBody, StandardCharsets.UTF_8);
            return HmacVerifier.matchesHex(webhookSecret, signedPayload, v1);
        } catch (Exception e) {
            log.error("[Stripe] Webhook verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private HttpHeaders buildHeaders(String idempotencyKey) {
        var h = new HttpHeaders();
        h.setBearerAuth(secretKey);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (idempotencyKey != null) h.set("Idempotency-Key", idempotencyKey);
        return h;
    }

    private GatewayPaymentStatus mapStatus(String st) {
        if (st == null) return GatewayPaymentStatus.PENDING;
        return switch (st) {
            case "succeeded"                          -> GatewayPaymentStatus.PAID;
            case "requires_payment_method",
                 "requires_confirmation",
                 "requires_action", "processing"     -> GatewayPaymentStatus.PENDING;
            case "canceled"                           -> GatewayPaymentStatus.CANCELED;
            default                                   -> GatewayPaymentStatus.FAILED;
        };
    }
}
