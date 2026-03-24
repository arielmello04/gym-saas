package com.gymsystem.payments.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Pagar.me v5 gateway implementation.
 *
 * Activate with:   payments.gateway=pagarme
 * Required config: payments.pagarme.secret-key
 *                  payments.pagarme.webhook-secret
 *
 * Supports: Pix, Credit Card, Boleto
 * Docs: https://docs.pagar.me/reference
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "pagarme")
@RequiredArgsConstructor
public class PagarmeGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.pagar.me/core/v5";

    @Value("${payments.pagarme.secret-key}")
    private String secretKey;

    @Value("${payments.pagarme.webhook-secret}")
    private String webhookSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GatewayChargeResponse charge(GatewayChargeRequest req) {
        try {
            var customer = Map.of(
                "name",  req.getCustomerName() != null ? req.getCustomerName() : "Cliente",
                "email", req.getCustomerEmail(),
                "document", req.getCustomerDocument() != null ? req.getCustomerDocument() : "",
                "document_type", "CPF",
                "type", "individual"
            );

            var item = Map.of(
                "amount",      (int) req.getAmountCents(),
                "description", req.getDescription() != null ? req.getDescription() : "GymSystem",
                "quantity",    1,
                "code",        req.getIdempotencyKey()
            );

            Map<String, Object> payment;
            if ("pix".equalsIgnoreCase(req.getPaymentMethod())) {
                payment = Map.of(
                    "payment_method", "pix",
                    "pix", Map.of("expires_in", 3600)
                );
            } else if ("boleto".equalsIgnoreCase(req.getPaymentMethod())) {
                payment = Map.of(
                    "payment_method", "boleto",
                    "boleto", Map.of("due_at", Instant.now().plusSeconds(3 * 86400).toString(),
                                     "instructions", "Pagar até o vencimento")
                );
            } else {
                // credit_card: requires card token — caller must enrich request
                payment = Map.of("payment_method", "credit_card");
            }

            var body = Map.of(
                "customer",           customer,
                "items",              new Object[]{item},
                "payments",           new Object[]{payment},
                "closed",             true
            );

            var response = restTemplate.exchange(
                    BASE_URL + "/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders(req.getIdempotencyKey())),
                    Map.class
            );

            var data     = response.getBody();
            String orderId = (String) data.get("id");
            String status  = (String) data.get("status");

            // Extract Pix / Boleto details from charges array
            String pixCode = null, pixQrCode = null, boletoBarcode = null;
            var charges = (java.util.List<?>) data.get("charges");
            if (charges != null && !charges.isEmpty()) {
                var charge       = (Map<?, ?>) charges.get(0);
                var lastTx       = (Map<?, ?>) charge.get("last_transaction");
                if (lastTx != null) {
                    pixCode      = (String) lastTx.get("qr_code");
                    pixQrCode    = (String) lastTx.get("qr_code_url");
                    boletoBarcode = (String) lastTx.get("line");
                }
            }

            return GatewayChargeResponse.builder()
                    .providerRef(orderId)
                    .status(mapStatus(status))
                    .pixCode(pixCode)
                    .pixQrCode(pixQrCode)
                    .boletoBarcode(boletoBarcode)
                    .rawResponse(objectMapper.writeValueAsString(data))
                    .build();

        } catch (Exception e) {
            log.error("[Pagarme] Charge failed: {}", e.getMessage(), e);
            throw new RuntimeException("Pagar.me charge failed: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayStatusResponse status(String providerRef) {
        try {
            var response = restTemplate.exchange(
                    BASE_URL + "/orders/" + providerRef,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(null)),
                    Map.class
            );
            var data   = response.getBody();
            String st  = (String) data.get("status");
            return GatewayStatusResponse.builder()
                    .providerRef(providerRef)
                    .status(mapStatus(st))
                    .build();
        } catch (Exception e) {
            log.error("[Pagarme] Status check failed: {}", e.getMessage());
            throw new RuntimeException("Pagar.me status check failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void refund(String providerRef, Long amountCents) {
        try {
            // Pagar.me: cancel the order (full) or create a refund charge
            restTemplate.exchange(
                    BASE_URL + "/orders/" + providerRef + "/closed",
                    HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("status", "canceled"), buildHeaders(null)),
                    Map.class
            );
            log.info("[Pagarme] Refund/cancel OK for ref={}", providerRef);
        } catch (Exception e) {
            log.error("[Pagarme] Refund failed: {}", e.getMessage());
            throw new RuntimeException("Pagar.me refund failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, byte[] rawBody) {
        try {
            // Pagar.me signs with HMAC-SHA256; secret is the API secret key
            String signature = headers.get("x-hub-signature");
            if (signature == null) return false;
            if (signature.startsWith("sha256=")) signature = signature.substring(7);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(rawBody));

            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("[Pagarme] Webhook verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private HttpHeaders buildHeaders(String idempotencyKey) {
        var h = new HttpHeaders();
        // Pagar.me v5 uses Basic auth with secret key as username
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        h.set("Authorization", "Basic " + encoded);
        h.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) h.set("Idempotency-Key", idempotencyKey);
        return h;
    }

    private GatewayPaymentStatus mapStatus(String st) {
        if (st == null) return GatewayPaymentStatus.PENDING;
        return switch (st) {
            case "paid", "partial_canceled" -> GatewayPaymentStatus.PAID;
            case "pending"                  -> GatewayPaymentStatus.PENDING;
            case "canceled"                 -> GatewayPaymentStatus.CANCELED;
            case "failed"                   -> GatewayPaymentStatus.FAILED;
            default                         -> GatewayPaymentStatus.PENDING;
        };
    }
}
