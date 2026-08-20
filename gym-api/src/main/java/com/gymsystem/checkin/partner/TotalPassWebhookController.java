package com.gymsystem.checkin.partner;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Recebe os check-ins que a TotalPass empurra.
 *
 * A URL registrada no parceiro carrega a academia e um segredo:
 *
 *   POST /api/v1/checkin/webhook/totalpass/{slug}/{segredo}
 *
 * A academia vai no caminho porque a TotalPass não manda cabeçalho de tenant —
 * o payload traz o código da unidade, mas registrar uma URL por academia é mais
 * direto e não depende de mapear esse código. O segredo está aí porque a
 * TotalPass não assina o webhook: sem ele, qualquer um que descobrisse a URL
 * criaria entradas na academia.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/checkin/webhook/totalpass")
@RequiredArgsConstructor
@Tag(name = "Check-in", description = "Webhook de check-in da TotalPass")
public class TotalPassWebhookController {

    private final PartnerCheckinService partnerCheckinService;
    private final TenantRepository tenantRepository;

    @Value("${checkin.partners.webhook-secret:change-me}")
    private String webhookSecret;

    @Operation(summary = "Recebe o evento CHECK_IN_CREATED da TotalPass")
    @PostMapping("/{slug}/{secret}")
    public ResponseEntity<Void> receive(@PathVariable String slug,
                                        @PathVariable String secret,
                                        @RequestBody JsonNode payload) {

        if (!secretMatches(secret)) {
            log.warn("[TotalPass] Webhook com segredo invalido para a academia {}", slug);
            return ResponseEntity.status(403).build();
        }

        var tenant = tenantRepository.findBySlugAndActiveTrue(slug);
        if (tenant.isEmpty()) {
            log.warn("[TotalPass] Webhook para academia desconhecida ou inativa: {}", slug);
            return ResponseEntity.notFound().build();
        }

        String tipo = payload.path("type").asText("");
        if (!"CHECK_IN_CREATED".equals(tipo)) {
            // Outros eventos não interessam aqui; responder 200 evita reenvio.
            log.debug("[TotalPass] Evento ignorado: {}", tipo);
            return ResponseEntity.ok().build();
        }

        String confirmUrl = payload.path("endpoint").asText(null);
        if (confirmUrl == null || confirmUrl.isBlank()) {
            log.warn("[TotalPass] Webhook sem link de confirmacao");
            return ResponseEntity.badRequest().build();
        }

        JsonNode checkIn = payload.path("check_in");
        JsonNode user    = payload.path("user");
        JsonNode place   = payload.path("place");

        partnerCheckinService.receive(
                tenant.get().getId(),
                CheckinProvider.TOTALPASS,
                new PartnerCheckinService.InboundCheckin(
                        confirmUrl,
                        text(user, "code"),
                        text(user, "name"),
                        text(user, "document_number"),
                        text(checkIn, "plan_code"),
                        text(place, "code"),
                        instant(checkIn, "started_at"),
                        instant(checkIn, "expires_at")));

        return ResponseEntity.ok().build();
    }

    /** Comparação em tempo constante: o segredo viaja na URL. */
    private boolean secretMatches(String provided) {
        if (provided == null || webhookSecret == null) return false;
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    /** As datas vêm com deslocamento (-03:00), não em UTC. */
    private static Instant instant(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) return null;
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(raw);
            } catch (Exception ex) {
                log.warn("[TotalPass] Data em formato inesperado: {}", raw);
                return null;
            }
        }
    }
}
