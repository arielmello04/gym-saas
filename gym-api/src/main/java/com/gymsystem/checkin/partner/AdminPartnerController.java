package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Operação dos parceiros de check-in: vínculo dos alunos e fila de entradas
 * recebidas por webhook.
 */
@RestController
@RequestMapping("/api/v1/admin/partners")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_MANAGER', 'ROLE_STAFF', 'ROLE_ADMIN_APP', 'ROLE_ADMIN_WEB')")
@Tag(name = "Admin — Parceiros", description = "Wellhub e TotalPass: vínculos e check-ins recebidos")
public class AdminPartnerController {

    private final PartnerMemberLinkRepository linkRepository;
    private final PartnerTenantConfigRepository configRepository;
    private final PartnerCredentialsService credentials;
    private final PartnerCheckinService partnerCheckins;
    private final CheckinPartnerRegistry partners;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${checkin.partners.webhook-secret:change-me}")
    private String webhookSecret;

    // ── Vínculo do aluno com o parceiro ───────────────────────

    @Operation(summary = "Lista os vínculos de alunos com os parceiros")
    @GetMapping("/links")
    public ResponseEntity<List<LinkResponse>> links() {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(linkRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
                .stream().map(LinkResponse::from).toList());
    }

    @Operation(summary = "Cadastra ou atualiza a identidade do aluno no parceiro")
    @PutMapping("/links")
    @Transactional
    public ResponseEntity<LinkResponse> upsertLink(@Valid @RequestBody UpsertLinkRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        var  provider = CheckinProvider.fromInput(req.getProvider());

        if (provider == CheckinProvider.DIRECT) {
            throw new IllegalArgumentException("Check-in direto nao tem identidade de parceiro");
        }

        var user = userRepository.findByEmail(req.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("Aluno nao encontrado: " + req.getEmail()));

        var link = linkRepository
                .findByTenantIdAndUserIdAndProvider(tenantId, user.getId(), provider)
                .orElseGet(() -> PartnerMemberLink.builder()
                        .tenant(tenant).user(user).provider(provider)
                        .createdAt(Instant.now())
                        .build());

        link.setExternalId(req.getExternalId().trim());
        link.setCustomCode(blankToNull(req.getCustomCode()));
        link.setDocument(blankToNull(req.getDocument()));
        link.setUpdatedAt(Instant.now());

        return ResponseEntity.ok(LinkResponse.from(linkRepository.save(link)));
    }

    @Operation(summary = "Remove o vínculo do aluno com o parceiro")
    @DeleteMapping("/links/{id}")
    @Transactional
    public ResponseEntity<Void> deleteLink(@PathVariable Long id) {
        Long tenantId = TenantGuard.currentTenantId();
        linkRepository.findById(id)
                .filter(l -> l.getTenant().getId().equals(tenantId))
                .ifPresent(linkRepository::delete);
        return ResponseEntity.noContent().build();
    }

    // ── Check-ins recebidos por webhook ───────────────────────

    @Operation(summary = "Check-ins de parceiro aguardando liberação")
    @GetMapping("/checkins/pending")
    public ResponseEntity<List<PendingResponse>> pending() {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(partnerCheckins.pending(tenantId)
                .stream().map(PendingResponse::from).toList());
    }

    @Operation(summary = "Libera a entrada confirmando o check-in no parceiro. "
            + "Na primeira visita, informe o e-mail do aluno para criar o vínculo.")
    @PostMapping("/checkins/{id}/confirm")
    public ResponseEntity<PendingResponse> confirm(
            @PathVariable Long id,
            @RequestParam(name = "email", required = false) String email) {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(PendingResponse.from(partnerCheckins.confirm(tenantId, id, email)));
    }

    // ── Configuração da integração ────────────────────────────

    @Operation(summary = "Testa credencial e alcance de cada parceiro nesta academia, sem liberar entrada nenhuma")
    @GetMapping("/diagnostics")
    public ResponseEntity<List<PartnerHealth>> diagnostics() {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(partners.health(p -> credentials.forTenant(tenantId, p)));
    }

    // ── Credenciais desta academia ────────────────────────────

    @Operation(summary = "Credenciais de parceiro desta academia")
    @GetMapping("/config")
    public ResponseEntity<List<ConfigResponse>> configs() {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(configRepository.findByTenantId(tenantId)
                .stream().map(ConfigResponse::from).toList());
    }

    @Operation(summary = "Cadastra o Gym ID (Wellhub) ou a place_api_key (TotalPass) desta academia")
    @PutMapping("/config")
    @Transactional
    public ResponseEntity<ConfigResponse> upsertConfig(@Valid @RequestBody UpsertConfigRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        var  provider = CheckinProvider.fromInput(req.getProvider());

        if (provider == CheckinProvider.DIRECT) {
            throw new IllegalArgumentException("Check-in direto nao tem credencial de parceiro");
        }

        var config = configRepository.findByTenantIdAndProvider(tenantId, provider)
                .orElseGet(() -> PartnerTenantConfig.builder()
                        .tenant(tenant).provider(provider)
                        .createdAt(Instant.now())
                        .build());

        if (provider == CheckinProvider.WELLHUB) {
            if (req.getGymId() == null || req.getGymId().isBlank()) {
                throw new IllegalArgumentException("Informe o Gym ID do Wellhub desta academia");
            }
            config.setGymId(req.getGymId().trim());
        } else {
            if (req.getPlaceApiKey() == null || req.getPlaceApiKey().isBlank()) {
                throw new IllegalArgumentException("Informe a place_api_key da TotalPass desta academia");
            }
            config.setPlaceApiKey(req.getPlaceApiKey().trim());
        }

        config.setActive(req.getActive() == null || req.getActive());
        config.setUpdatedAt(Instant.now());

        return ResponseEntity.ok(ConfigResponse.from(configRepository.save(config)));
    }

    @Operation(summary = "URL de webhook a registrar no portal do parceiro")
    @GetMapping("/webhook-url")
    public ResponseEntity<WebhookUrlResponse> webhookUrl() {
        TenantGuard.currentTenantId();
        String slug = TenantContext.getTenantSlug();
        String url  = "%s/api/v1/checkin/webhook/totalpass/%s/%s".formatted(baseUrl, slug, webhookSecret);
        return ResponseEntity.ok(new WebhookUrlResponse(url, !"change-me".equals(webhookSecret)));
    }

    @Operation(summary = "Registra a URL de webhook direto na API do parceiro")
    @PostMapping("/webhook-url/register")
    public ResponseEntity<WebhookRegisterResponse> registerWebhook() {
        String slug = TenantContext.getTenantSlug();
        String url  = "%s/api/v1/checkin/webhook/totalpass/%s/%s".formatted(baseUrl, slug, webhookSecret);
        Long tenantId = TenantGuard.currentTenantId();
        boolean ok  = partners.requirePush(CheckinProvider.TOTALPASS)
                .registerWebhook(credentials.forTenant(tenantId, CheckinProvider.TOTALPASS), url);
        return ResponseEntity.ok(new WebhookRegisterResponse(url, ok));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ── DTOs ──────────────────────────────────────────────────

    @Data
    public static class UpsertLinkRequest {
        @NotBlank private String provider;   // WELLHUB | TOTALPASS
        @NotBlank private String email;      // aluno já cadastrado no sistema
        @NotBlank private String externalId; // Wellhub: gympass_id de 13 dígitos
        private String customCode;           // Wellhub: PIN/QR interno
        private String document;             // TotalPass: CPF, como vem no webhook
    }

    public record LinkResponse(
            Long id, String provider, String email,
            String externalId, String customCode, String document, Instant updatedAt
    ) {
        static LinkResponse from(PartnerMemberLink l) {
            return new LinkResponse(l.getId(), l.getProvider().name(), l.getUser().getEmail(),
                    l.getExternalId(), l.getCustomCode(), l.getDocument(), l.getUpdatedAt());
        }
    }

    public record PendingResponse(
            Long id, String provider, String userName, String userEmail,
            String document, String planCode, String status, String failureReason,
            Instant startedAt, Instant expiresAt
    ) {
        static PendingResponse from(PartnerCheckinEvent e) {
            return new PendingResponse(
                    e.getId(), e.getProvider().name(), e.getUserName(),
                    e.getUser() != null ? e.getUser().getEmail() : null,
                    e.getUserDocument(), e.getPlanCode(), e.getStatus().name(),
                    e.getFailureReason(), e.getStartedAt(), e.getExpiresAt());
        }
    }

    @Data
    public static class UpsertConfigRequest {
        @NotBlank private String provider;  // WELLHUB | TOTALPASS
        private String gymId;               // Wellhub
        private String placeApiKey;         // TotalPass
        private Boolean active;
    }

    /**
     * A place_api_key é segredo: sai mascarada, só com os últimos caracteres,
     * o suficiente para a academia conferir que cadastrou a chave certa.
     */
    public record ConfigResponse(
            Long id, String provider, String gymId, String placeApiKeyMasked,
            boolean active, Instant updatedAt
    ) {
        static ConfigResponse from(PartnerTenantConfig c) {
            return new ConfigResponse(c.getId(), c.getProvider().name(), c.getGymId(),
                    mascarar(c.getPlaceApiKey()), c.isActive(), c.getUpdatedAt());
        }

        private static String mascarar(String chave) {
            if (chave == null || chave.isBlank()) return null;
            return chave.length() <= 4 ? "••••" : "••••" + chave.substring(chave.length() - 4);
        }
    }

    public record WebhookUrlResponse(String url, boolean secretConfigured) {}
    public record WebhookRegisterResponse(String url, boolean registered) {}
}
