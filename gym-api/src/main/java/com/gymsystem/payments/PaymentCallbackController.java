package com.gymsystem.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsystem.payments.gateway.GatewayPaymentStatus;
import com.gymsystem.payments.gateway.PaymentGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified webhook endpoint for all payment providers.
 * Verifies the signature via the active PaymentGateway implementation,
 * then delegates status handling to SubscriptionService.
 *
 * URL: POST /api/v1/payments/callback/webhook
 * This URL must be registered in the provider's dashboard as the webhook endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment gateway webhooks")
public class PaymentCallbackController {

    private final PaymentGateway      paymentGateway;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper        objectMapper;

    @Operation(summary = "Webhook receiver for payment gateway notifications")
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            HttpServletRequest request,
            @RequestBody byte[] rawBody) {

        try {
            // Collect headers into a map (lowercase keys)
            Map<String, String> headers = new HashMap<>();
            var iter = request.getHeaderNames();
            while (iter.hasMoreElements()) {
                String name = iter.nextElement();
                headers.put(name.toLowerCase(), request.getHeader(name));
            }

            // Verify signature using the active gateway implementation
            if (!paymentGateway.verifyWebhook(headers, rawBody)) {
                log.warn("[Webhook] Invalid signature — rejected");
                return ResponseEntity.status(401).build();
            }

            // Parse the payload
            JsonNode root        = objectMapper.readTree(rawBody);
            String   providerRef = extractProviderRef(root);
            GatewayPaymentStatus status = extractStatus(root);

            if (providerRef == null) {
                log.warn("[Webhook] Could not extract providerRef from payload");
                return ResponseEntity.badRequest().build();
            }

            log.info("[Webhook] Received event: ref={} status={}", providerRef, status);
            subscriptionService.handleGatewayEvent(providerRef, status);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("[Webhook] Processing error: {}", e.getMessage(), e);
            // Return 200 to prevent the provider from retrying endlessly on our internal errors
            return ResponseEntity.ok().build();
        }
    }

    // ── Payload normalization ─────────────────────────────────
    // Each gateway has a different payload shape. We normalize here
    // so SubscriptionService stays provider-agnostic.

    private String extractProviderRef(JsonNode root) {
        // Mercado Pago e Pagar.me: { "data": { "id": "..." } }
        if (root.has("data") && root.get("data").has("id"))
            return root.get("data").get("id").asText();

        // Stripe: { "data": { "object": { "id": "pi_..." } } }
        if (root.has("data") && root.get("data").has("object"))
            return root.get("data").get("object").get("id").asText(null);

        // Generic fallback
        if (root.has("id")) return root.get("id").asText();
        return null;
    }

    private GatewayPaymentStatus extractStatus(JsonNode root) {
        // Mercado Pago: { "type": "payment", "action": "payment.updated", "data": { "id": "...", "status": "approved" } }
        if (root.has("action")) {
            String action = root.get("action").asText("");
            if (action.contains("payment")) {
                // Status is fetched separately via gateway.status() in full impl
                // For webhook, MP sends action type not status — treat as needing status check
                return GatewayPaymentStatus.PENDING;
            }
        }

        // Pagar.me: { "type": "order.paid" }
        if (root.has("type")) {
            String type = root.get("type").asText("");
            if (type.contains("paid"))      return GatewayPaymentStatus.PAID;
            if (type.contains("failed") || type.contains("cancel")) return GatewayPaymentStatus.FAILED;
        }

        // Stripe: { "type": "payment_intent.succeeded" }
        if (root.has("type")) {
            String type = root.get("type").asText("");
            if (type.equals("payment_intent.succeeded"))                return GatewayPaymentStatus.PAID;
            if (type.equals("payment_intent.payment_failed"))           return GatewayPaymentStatus.FAILED;
            if (type.equals("payment_intent.canceled"))                 return GatewayPaymentStatus.CANCELED;
        }

        return GatewayPaymentStatus.PENDING;
    }
}
