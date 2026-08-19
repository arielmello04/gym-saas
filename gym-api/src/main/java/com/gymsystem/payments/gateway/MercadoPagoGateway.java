package com.gymsystem.payments.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsystem.payments.webhook.HmacVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Mercado Pago gateway implementation.
 *
 * Activate with:   payments.gateway=mercadopago
 * Required config: payments.mercadopago.access-token
 *                  payments.mercadopago.webhook-secret
 *
 * Supports: Pix (primary), Credit Card, Boleto
 * Docs: https://www.mercadopago.com.br/developers/pt/docs
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "mercadopago")
@RequiredArgsConstructor
public class MercadoPagoGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.mercadopago.com";

    @Value("${payments.mercadopago.access-token}")
    private String accessToken;

    @Value("${payments.mercadopago.webhook-secret}")
    private String webhookSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest req) {
        try {
            // Build Mercado Pago payment request
            var body = Map.of(
                "transaction_amount", req.getAmountCents() / 100.0,
                "currency_id",        req.getCurrency(),
                "description",        req.getDescription() != null ? req.getDescription() : "GymSystem",
                "payment_method_id",  mapPaymentMethod(req.getPaymentMethod()),
                "payer", Map.of(
                    "email", req.getCustomerEmail(),
                    "first_name", req.getCustomerName() != null ? req.getCustomerName() : "",
                    "identification", Map.of(
                        "type",   "CPF",
                        "number", req.getCustomerDocument() != null ? req.getCustomerDocument() : ""
                    )
                ),
                "notification_url", req.getCallbackUrl() != null ? req.getCallbackUrl() : "",
                "external_reference", req.getIdempotencyKey()
            );

            var headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Idempotency-Key", req.getIdempotencyKey());

            var response = restTemplate.exchange(
                    BASE_URL + "/v1/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            var data = response.getBody();
            if (data == null) throw new RuntimeException("Empty response from Mercado Pago");

            String providerRef = String.valueOf(data.get("id"));
            String mpStatus    = String.valueOf(data.get("status"));

            // Extract Pix info if applicable
            String pixCode   = null;
            String pixQrCode = null;
            var txInfo = (Map<?, ?>) data.get("point_of_interaction");
            if (txInfo != null) {
                var txData = (Map<?, ?>) txInfo.get("transaction_data");
                if (txData != null) {
                    pixCode   = (String) txData.get("qr_code");
                    pixQrCode = (String) txData.get("qr_code_base64");
                }
            }

            return GatewayChargeResponse.builder()
                    .providerRef(providerRef)
                    .status(mapStatus(mpStatus))
                    .pixCode(pixCode)
                    .pixQrCode(pixQrCode)
                    .rawResponse(objectMapper.writeValueAsString(data))
                    .build();

        } catch (Exception e) {
            log.error("[MercadoPago] Charge failed: {}", e.getMessage(), e);
            throw new RuntimeException("MercadoPago charge failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayStatusResponse status(String providerRef) {
        try {
            var headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            var response = restTemplate.exchange(
                    BASE_URL + "/v1/payments/" + providerRef,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            var data   = response.getBody();
            String mpStatus = String.valueOf(data.get("status"));
            String paidAtStr = (String) data.get("date_approved");

            return GatewayStatusResponse.builder()
                    .providerRef(providerRef)
                    .status(mapStatus(mpStatus))
                    .paidAt(paidAtStr != null ? Instant.parse(paidAtStr) : null)
                    .build();

        } catch (Exception e) {
            log.error("[MercadoPago] Status check failed: {}", e.getMessage());
            throw new RuntimeException("MercadoPago status check failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void refund(String providerRef, Long amountCents) {
        try {
            var headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Object body = amountCents != null
                    ? Map.of("amount", amountCents / 100.0)
                    : Map.of();

            restTemplate.exchange(
                    BASE_URL + "/v1/payments/" + providerRef + "/refunds",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            log.info("[MercadoPago] Refund OK for ref={}", providerRef);

        } catch (Exception e) {
            log.error("[MercadoPago] Refund failed: {}", e.getMessage());
            throw new RuntimeException("MercadoPago refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, byte[] rawBody) {
        try {
            // Mercado Pago signs with HMAC-SHA256
            // Header: x-signature contains ts=...v1=...
            String xSignature = headers.get("x-signature");
            String xRequestId = headers.get("x-request-id");
            if (xSignature == null) return false;

            String ts = null, v1 = null;
            for (String part : xSignature.split(",")) {
                if (part.startsWith("ts=")) ts = part.substring(3);
                if (part.startsWith("v1=")) v1 = part.substring(3);
            }
            if (ts == null || v1 == null) return false;

            String manifest = "id:" + xRequestId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
            return HmacVerifier.matchesHex(webhookSecret, manifest, v1);

        } catch (Exception e) {
            log.error("[MercadoPago] Webhook verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private GatewayPaymentStatus mapStatus(String mpStatus) {
        return switch (mpStatus) {
            case "approved"            -> GatewayPaymentStatus.PAID;
            case "pending", "in_process", "authorized" -> GatewayPaymentStatus.PENDING;
            case "rejected", "cancelled" -> GatewayPaymentStatus.FAILED;
            case "refunded", "charged_back" -> GatewayPaymentStatus.REFUNDED;
            default -> GatewayPaymentStatus.PENDING;
        };
    }

    private String mapPaymentMethod(String method) {
        if (method == null) return "pix";
        return switch (method.toLowerCase()) {
            case "pix"         -> "pix";
            case "boleto"      -> "bolbradesco";
            case "credit_card" -> "visa"; // overridden by card token in full impl
            default            -> "pix";
        };
    }
}
