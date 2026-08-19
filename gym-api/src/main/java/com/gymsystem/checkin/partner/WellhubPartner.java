// src/main/java/com/gymsystem/checkin/partner/WellhubPartner.java
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wellhub (ex-Gympass) — Access Control API.
 *
 * Fluxo real, conforme developers.wellhub.com:
 *
 *   1. O aluno faz check-in no app do Wellhub (fora do nosso sistema).
 *   2. A academia chama POST /access/v1/validate com o gympass_id dele.
 *   3. A validacao gera a transacao que paga a academia depois.
 *
 * Ou seja: o que identifica o aluno e o gympass_id de 13 digitos, guardado no
 * PartnerMemberLink desde a primeira visita - nao um codigo digitado na
 * recepcao. O check-in tem prazo de validade; fora dele o Wellhub responde 404.
 *
 * Autenticacao: bearer token estatico entregue pelo Wellhub (nao e OAuth2).
 * Integracoes antigas com X-Api-Key seguem funcionando, mas estao a caminho da
 * depreciacao, entao aqui so o bearer.
 *
 * Configuracao GLOBAL (da integradora):
 *   checkin.partners.wellhub.mode      mock | live
 *   checkin.partners.wellhub.base-url  producao ou sandbox
 *   checkin.partners.wellhub.token     bearer entregue pelo Wellhub
 *
 * Configuracao POR ACADEMIA: o X-Gym-Id, cadastrado em Parceiros e recebido
 * aqui em PartnerCredentials. O mesmo token cobre varias unidades - o header e
 * que diz qual delas.
 */
@Slf4j
@Component
public class WellhubPartner implements ValidatingPartner {

    private static final String VALIDATE_PATH = "/validate";
    private static final String CODE_PATH     = "/code/";
    private static final String GYM_ID_HEADER = "X-Gym-Id";

    /** Id impossivel usado so para sondar credencial: 13 digitos que nao existem. */
    private static final String SONDA_ID = "0000000000000";

    @Value("${checkin.partners.wellhub.mode:mock}")
    private String mode;

    @Value("${checkin.partners.wellhub.base-url:https://api.partners.gympass.com/access/v1}")
    private String baseUrl;

    @Value("${checkin.partners.wellhub.token:}")
    private String token;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CheckinProvider provider() {
        return CheckinProvider.WELLHUB;
    }

    @Override
    public boolean isConfigured() {
        if (isMock()) return true;
        return !token.isBlank();
    }

    /**
     * Testa credencial e alcance sem liberar entrada nenhuma.
     *
     * Não existe endpoint de ping na Access Control API, então a sonda é uma
     * validação com um gympass_id impossível: se o Wellhub responde 400/404,
     * ele nos autenticou e só não achou o check-in — que é exatamente o que
     * queremos saber. 403 é credencial recusada.
     */
    @Override
    public PartnerHealth check(PartnerCredentials credentials) {
        if (isMock()) return PartnerHealth.mock(provider().name());
        if (token.isBlank()) {
            return PartnerHealth.naoConfigurado(provider().name(),
                    "WELLHUB_TOKEN (credencial da integradora)");
        }
        if (!credentials.temGymId()) {
            return PartnerHealth.naoConfigurado(provider().name(),
                    "Gym ID desta academia (cadastre em Parceiros)");
        }

        try {
            restTemplate.exchange(
                    baseUrl + VALIDATE_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("gympass_id", SONDA_ID), headers(credentials)),
                    String.class);
            // Improvável, mas se aceitar: credencial válida do mesmo jeito.
            return PartnerHealth.ok(provider().name(), "Wellhub respondeu e aceitou a credencial.");

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN || e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return PartnerHealth.credencialRecusada(provider().name(),
                        "Wellhub recusou a credencial. Confira WELLHUB_TOKEN e o Gym ID desta academia.");
            }
            return PartnerHealth.ok(provider().name(),
                    "Wellhub respondeu e aceitou a credencial (sonda devolveu "
                    + e.getStatusCode().value() + ", como esperado).");

        } catch (Exception e) {
            return PartnerHealth.inacessivel(provider().name(),
                    "Nao foi possivel alcancar " + baseUrl + ": " + e.getMessage());
        }
    }

    /**
     * Valida a entrada do aluno.
     *
     * @param memberRef gympass_id de 13 digitos
     * @param customCode codigo interno da academia a associar ao aluno (opcional)
     */
    @Override
    public PartnerValidation validate(PartnerCredentials credentials, String memberRef, String customCode) {
        if (memberRef == null || memberRef.isBlank()) {
            return PartnerValidation.denied("Aluno sem Wellhub ID cadastrado");
        }
        if (isMock()) {
            return mockValidate(memberRef);
        }
        if (!isConfigured()) {
            return PartnerValidation.denied("Wellhub nao configurado nesta instalacao");
        }
        if (!credentials.temGymId()) {
            return PartnerValidation.denied("Academia sem Gym ID do Wellhub cadastrado");
        }

        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("gympass_id", memberRef.trim());
            if (customCode != null && !customCode.isBlank()) {
                body.put("custom_code", customCode.trim());
            }

            var response = restTemplate.exchange(
                    baseUrl + VALIDATE_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers(credentials)),
                    String.class);

            return parse(memberRef, response.getBody());

        } catch (HttpStatusCodeException e) {
            // 400/404 sao recusa de negocio (check-in inexistente, expirado ou
            // ja validado); 403 e credencial invalida. Nenhum e falha nossa.
            String detail = extractError(e.getResponseBodyAsString(), e.getStatusCode());
            log.info("[Wellhub] Check-in recusado ({}): {}", e.getStatusCode(), detail);
            return PartnerValidation.denied(detail);

        } catch (Exception e) {
            log.error("[Wellhub] Falha ao validar check-in: {}", e.getMessage());
            return PartnerValidation.denied("Wellhub indisponivel no momento");
        }
    }

    /**
     * Cria ou atualiza o codigo interno (PIN/QR) que o aluno usa para entrar.
     *
     * Nao e obrigatorio para validar, mas e o que permite a catraca da academia
     * reconhecer o aluno do Wellhub pelo mesmo meio dos demais.
     */
    public boolean upsertCustomCode(PartnerCredentials credentials, String wellhubId, String customCode) {
        if (isMock()) {
            log.info("[Wellhub][mock] custom_code {} associado a {}", customCode, wellhubId);
            return true;
        }
        if (!isConfigured() || !credentials.temGymId()) return false;

        var body = Map.of("custom_code", customCode);
        var url  = baseUrl + CODE_PATH + wellhubId;
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers(credentials)), String.class);
            return true;
        } catch (HttpStatusCodeException e) {
            // 409: ja existe um codigo para este aluno nesta academia -> atualiza.
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                try {
                    restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers(credentials)), String.class);
                    return true;
                } catch (Exception ex) {
                    log.error("[Wellhub] Falha ao atualizar custom_code: {}", ex.getMessage());
                    return false;
                }
            }
            log.error("[Wellhub] Falha ao criar custom_code ({}): {}", e.getStatusCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[Wellhub] Falha ao criar custom_code: {}", e.getMessage());
            return false;
        }
    }

    /** Bearer da integradora + o X-Gym-Id da academia desta requisição. */
    private HttpHeaders headers(PartnerCredentials credentials) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set(GYM_ID_HEADER, credentials.gymId());
        return headers;
    }

    // ── Resposta ──────────────────────────────────────────────

    /**
     * Le a resposta da validacao.
     *
     * A documentacao descreve dois formatos de sucesso: 200 sem corpo, e 200 com
     * um envelope metadata/results. Os dois valem como entrada liberada; o
     * envelope so acrescenta o produto e o horario da validacao.
     */
    private PartnerValidation parse(String memberRef, String rawBody) throws Exception {
        if (rawBody == null || rawBody.isBlank()) {
            return PartnerValidation.approved(memberRef, null, null);
        }

        JsonNode root = objectMapper.readTree(rawBody);
        log.debug("[Wellhub] Resposta de validacao: {}", rawBody);

        // Envelope de erro pode vir com HTTP 200 em algumas rotas.
        JsonNode erros = root.path("errors");
        if (erros.isArray() && !erros.isEmpty()) {
            return PartnerValidation.denied(erros.get(0).path("message").asText("Check-in recusado pelo Wellhub"));
        }

        JsonNode results = root.path("results");
        String   idAluno = results.path("user").path("gympass_id").asText(memberRef);
        String   produto = results.path("gym").path("product").path("description").asText(null);

        return PartnerValidation.approved(idAluno, null, produto);
    }

    private String extractError(String rawBody, HttpStatusCode status) {
        String padrao = switch (status.value()) {
            case 404 -> "Nenhum check-in ativo no Wellhub para este aluno";
            case 400 -> "Check-in do Wellhub expirado ou ja validado";
            case 403 -> "Credencial do Wellhub invalida";
            default  -> "Check-in recusado pelo Wellhub";
        };
        try {
            if (rawBody == null || rawBody.isBlank()) return padrao;
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode erros = root.path("errors");
            if (erros.isArray() && !erros.isEmpty()) {
                String msg = erros.get(0).path("message").asText(null);
                if (msg != null && !msg.isBlank()) return msg;
            }
            return padrao;
        } catch (Exception e) {
            return padrao;
        }
    }

    // ── Modo mock ─────────────────────────────────────────────

    private boolean isMock() {
        return !"live".equalsIgnoreCase(mode);
    }

    /**
     * Permite exercitar o fluxo inteiro sem credencial de parceiro.
     * Wellhub ID comecando por "DENY" e recusado, para o caminho de erro.
     */
    private PartnerValidation mockValidate(String memberRef) {
        if (memberRef.toUpperCase().startsWith("DENY")) {
            return PartnerValidation.denied("Nenhum check-in ativo no Wellhub para este aluno (mock)");
        }
        log.info("[Wellhub][mock] Entrada liberada para o Wellhub ID {}", memberRef);
        return PartnerValidation.approved(memberRef, "Aluno Wellhub (mock)", "Silver");
    }
}
