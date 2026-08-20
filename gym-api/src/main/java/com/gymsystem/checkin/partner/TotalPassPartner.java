// src/main/java/com/gymsystem/checkin/partner/TotalPassPartner.java
package com.gymsystem.checkin.partner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymsystem.checkin.CheckinProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TotalPass — validação de check-in.
 *
 * Fluxo real, conforme dev.totalpass.com e a central de ajuda: quem inicia é a
 * TotalPass, não a academia.
 *
 *   1. Autenticamos a unidade com place_api_key + partner_api_key e recebemos um
 *      JWT válido por 24 horas.
 *   2. Registramos a URL desta instalação para receber os webhooks.
 *   3. O aluno faz check-in no app; a TotalPass chama a nossa URL com os dados
 *      e um link exclusivo de confirmação (campo "endpoint" do payload).
 *   4. Para liberar a entrada, fazemos POST nesse link — dentro de 90 minutos.
 *
 * O 422 na confirmação não é erro nosso: significa que a recepção já validou o
 * check-in manualmente pelo portal da TotalPass. A integração não bloqueia esse
 * caminho de propósito.
 *
 * Configuração GLOBAL (da integradora):
 *   checkin.partners.totalpass.mode             mock | live
 *   checkin.partners.totalpass.auth-url         endpoint de autenticação
 *   checkin.partners.totalpass.webhook-base-url base da API de webhooks
 *   checkin.partners.totalpass.partner-api-key  chave do ERP (confidencial,
 *                                               "nunca solicitada aos clientes")
 *
 * Configuração POR ACADEMIA: a place_api_key, que a própria academia gera no
 * portal da TotalPass e cadastra em Parceiros. Chega aqui em PartnerCredentials.
 */
@Slf4j
@Component
public class TotalPassPartner implements PushPartner {

    /** O JWT vale 24h; renovamos com folga para não usar um que vence na chamada. */
    private static final long TOKEN_TTL_SECONDS = 23 * 3600L;

    @Value("${checkin.partners.totalpass.mode:mock}")
    private String mode;

    @Value("${checkin.partners.totalpass.auth-url:https://booking-api.totalpass.com/partner/auth}")
    private String authUrl;

    @Value("${checkin.partners.totalpass.webhook-base-url:https://booking-api.totalpass.com/partner/webhook}")
    private String webhookBaseUrl;

    @Value("${checkin.partners.totalpass.partner-api-key:}")
    private String partnerApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * JWT por academia.
     *
     * Um cache único vazaria o token de uma unidade para outra: a chave do mapa
     * é a place_api_key, que é justamente o que distingue as academias.
     */
    private final Map<String, TokenCache> tokensPorAcademia = new ConcurrentHashMap<>();

    private record TokenCache(String jwt, Instant expiraEm) {}

    @Override
    public CheckinProvider provider() {
        return CheckinProvider.TOTALPASS;
    }

    @Override
    public boolean isConfigured() {
        if (isMock()) return true;
        return !partnerApiKey.isBlank();
    }

    /**
     * Testa credencial e alcance sem confirmar entrada nenhuma.
     *
     * Aqui há sonda natural: a própria autenticação. Se as duas chaves geram um
     * JWT, a integração está pronta para receber webhooks.
     */
    @Override
    public PartnerHealth check(PartnerCredentials credentials) {
        if (isMock()) return PartnerHealth.mock(provider().name());
        if (partnerApiKey.isBlank()) {
            return PartnerHealth.naoConfigurado(provider().name(),
                    "TOTALPASS_PARTNER_API_KEY (credencial da integradora)");
        }
        if (!credentials.temPlaceApiKey()) {
            return PartnerHealth.naoConfigurado(provider().name(),
                    "place_api_key desta academia (cadastre em Parceiros)");
        }

        try {
            // Força a renovação: um token em cache esconderia credencial trocada.
            tokensPorAcademia.remove(credentials.placeApiKey());
            accessToken(credentials);
            return PartnerHealth.ok(provider().name(),
                    "TotalPass autenticou. Falta registrar a URL de webhook, se ainda nao foi.");

        } catch (Exception e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            if (causa instanceof HttpStatusCodeException http) {
                if (http.getStatusCode() == HttpStatus.UNAUTHORIZED
                        || http.getStatusCode() == HttpStatus.FORBIDDEN) {
                    return PartnerHealth.credencialRecusada(provider().name(),
                            "TotalPass recusou as chaves. Confira TOTALPASS_PARTNER_API_KEY e a place_api_key desta academia.");
                }
                return PartnerHealth.credencialRecusada(provider().name(),
                        "TotalPass respondeu " + http.getStatusCode().value() + " na autenticacao.");
            }
            return PartnerHealth.inacessivel(provider().name(),
                    "Nao foi possivel alcancar " + authUrl + ": " + causa.getMessage());
        }
    }

    // ── Confirmação da entrada ────────────────────────────────

    @Override
    public PartnerValidation confirm(PartnerCredentials credentials, String confirmUrl) {
        if (confirmUrl == null || confirmUrl.isBlank()) {
            return PartnerValidation.denied("Check-in sem link de confirmacao");
        }
        if (isMock()) {
            return mockConfirm(confirmUrl);
        }
        if (!isConfigured()) {
            return PartnerValidation.denied("TotalPass nao configurado nesta instalacao");
        }
        if (!credentials.temPlaceApiKey()) {
            return PartnerValidation.denied("Academia sem place_api_key da TotalPass cadastrada");
        }

        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken(credentials));

            var response = restTemplate.exchange(
                    confirmUrl, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), String.class);

            log.info("[TotalPass] Entrada confirmada ({})", response.getStatusCode());
            return PartnerValidation.approved(null, null, null);

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                // A recepção validou pelo portal antes de nós. Não é falha.
                return PartnerValidation.denied("Check-in ja validado no portal da TotalPass");
            }
            String detail = extractError(e.getResponseBodyAsString(), e.getStatusCode());
            log.info("[TotalPass] Confirmacao recusada ({}): {}", e.getStatusCode(), detail);
            return PartnerValidation.denied(detail);

        } catch (Exception e) {
            log.error("[TotalPass] Falha ao confirmar check-in: {}", e.getMessage());
            return PartnerValidation.denied("TotalPass indisponivel no momento");
        }
    }

    // ── Registro do webhook ───────────────────────────────────

    @Override
    public boolean registerWebhook(PartnerCredentials credentials, String callbackUrl) {
        if (isMock()) {
            log.info("[TotalPass][mock] Webhook registrado em {}", callbackUrl);
            return true;
        }
        if (!isConfigured() || !credentials.temPlaceApiKey()) return false;

        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken(credentials));

            restTemplate.exchange(
                    webhookBaseUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("url", callbackUrl, "type", "CHECK_IN_CREATED"), headers),
                    String.class);

            log.info("[TotalPass] Webhook registrado em {}", callbackUrl);
            return true;

        } catch (HttpStatusCodeException e) {
            // Já registrado: o parceiro responde conflito, e isso é sucesso aqui.
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("[TotalPass] Webhook ja estava registrado");
                return true;
            }
            log.error("[TotalPass] Falha ao registrar webhook ({}): {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("[TotalPass] Falha ao registrar webhook: {}", e.getMessage());
            return false;
        }
    }

    // ── Autenticação ──────────────────────────────────────────

    /** JWT em cache por academia: vale 24h e não faz sentido pedir um a cada confirmação. */
    private String accessToken(PartnerCredentials credentials) {
        String chave = credentials.placeApiKey();
        TokenCache cache = tokensPorAcademia.get(chave);
        if (cache != null && Instant.now().isBefore(cache.expiraEm())) {
            return cache.jwt();
        }

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var body = Map.of(
                "place_api_key",   chave,
                "partner_api_key", partnerApiKey);

        var response = restTemplate.exchange(
                authUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String jwt = firstText(root, null, "token", "jwt", "access_token");
            if (jwt == null) {
                // Alguns retornos aninham em data/results.
                jwt = firstText(root.path("data"), null, "token", "jwt", "access_token");
            }
            if (jwt == null) throw new IllegalStateException("Resposta de autenticacao sem token");

            tokensPorAcademia.put(chave,
                    new TokenCache(jwt, Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
            return jwt;

        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel autenticar no TotalPass", e);
        }
    }

    private String extractError(String rawBody, HttpStatusCode status) {
        String padrao = switch (status.value()) {
            case 401, 403 -> "Credencial do TotalPass invalida";
            case 404      -> "Check-in nao encontrado no TotalPass";
            default       -> "Check-in recusado pelo TotalPass";
        };
        try {
            if (rawBody == null || rawBody.isBlank()) return padrao;
            JsonNode root = objectMapper.readTree(rawBody);
            return firstText(root, padrao, "message", "error", "detail");
        } catch (Exception e) {
            return padrao;
        }
    }

    private static String firstText(JsonNode node, String fallback, String... fields) {
        if (node == null) return fallback;
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return fallback;
    }

    // ── Modo mock ─────────────────────────────────────────────

    private boolean isMock() {
        return !"live".equalsIgnoreCase(mode);
    }

    private PartnerValidation mockConfirm(String confirmUrl) {
        if (confirmUrl.toUpperCase().contains("DENY")) {
            return PartnerValidation.denied("Check-in ja validado no portal da TotalPass (mock)");
        }
        log.info("[TotalPass][mock] Entrada confirmada em {}", confirmUrl);
        return PartnerValidation.approved(null, "Aluno TotalPass (mock)", null);
    }
}
