package com.gymsystem.checkin;

import com.gymsystem.checkin.dto.CheckinItem;
import com.gymsystem.checkin.dto.StartCheckinRequest;
import com.gymsystem.checkin.dto.StartCheckinResponse;
import com.gymsystem.checkin.partner.CheckinPartnerRegistry;
import com.gymsystem.checkin.partner.PartnerCredentialsService;
import com.gymsystem.checkin.partner.PartnerCheckinService;
import com.gymsystem.checkin.partner.PartnerMemberLink;
import com.gymsystem.checkin.partner.PartnerValidation;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinRepository repository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final CheckinPartnerRegistry partners;
    private final PartnerCheckinService partnerCheckins;
    private final PartnerCredentialsService credentials;

    @Value("${checkin.callback-secret}")
    private String callbackSecret;

    /**
     * Registra a entrada do aluno.
     *
     * O caminho depende de como o parceiro trabalha:
     *
     *   DIRECT  - aluno da casa, concluído na hora.
     *   WELLHUB - o aluno já fez check-in no app do Wellhub; consultamos a API
     *             deles com o Wellhub ID guardado no vínculo. Sem vínculo, a
     *             recepção pode informar o ID na hora (primeira visita).
     *   TOTALPASS - não passa por aqui: a entrada chega por webhook e é liberada
     *             na fila de check-ins recebidos.
     *
     * O registro é gravado antes da chamada externa para que a recusa também
     * fique no histórico — é o que se confere com o parceiro no fim do mês.
     */
    @Transactional
    public StartCheckinResponse start(StartCheckinRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        User user     = currentUser();

        CheckinProvider provider = CheckinProvider.fromInput(req.getProvider());

        if (provider == CheckinProvider.TOTALPASS) {
            throw new IllegalArgumentException(
                    "Check-in do TotalPass chega por webhook: libere pela fila de check-ins recebidos");
        }

        Checkin c = Checkin.builder()
                .user(user)
                .tenant(tenant)
                .provider(provider)
                .gymName(provider == CheckinProvider.DIRECT ? req.getGymName() : null)
                .providerRef("CHK-" + UUID.randomUUID())
                .status(CheckinStatus.STARTED)
                .startedAt(Instant.now())
                .build();
        repository.save(c);

        if (provider == CheckinProvider.DIRECT) {
            return complete(c, null);
        }

        // Wellhub: o ID informado na requisição tem prioridade (primeira visita),
        // senão vale o vínculo já cadastrado.
        PartnerMemberLink vinculo = partnerCheckins
                .link(tenantId, user.getId(), provider).orElse(null);

        String memberRef = req.getCode() != null && !req.getCode().isBlank()
                ? req.getCode().trim()
                : (vinculo != null ? vinculo.getExternalId() : null);

        if (memberRef == null) {
            c.setStatus(CheckinStatus.FAILED);
            c.setFailureReason("Aluno sem Wellhub ID cadastrado");
            repository.save(c);
            return new StartCheckinResponse(
                    c.getId(), provider.name(), c.getStatus().name(), false, null,
                    "Cadastre o Wellhub ID do aluno para validar a entrada");
        }

        String customCode = vinculo != null ? vinculo.getCustomCode() : null;
        PartnerValidation result = partners.requireValidating(provider)
                .validate(credentials.forTenant(tenantId, provider), memberRef, customCode);

        if (!result.approved()) {
            c.setStatus(CheckinStatus.FAILED);
            c.setFailureReason(result.reason());
            c.setPartnerMemberRef(memberRef);
            repository.save(c);
            log.info("[Check-in] {} recusado para {}: {}", provider, user.getEmail(), result.reason());
            return new StartCheckinResponse(
                    c.getId(), provider.name(), c.getStatus().name(), false, null, result.reason());
        }

        c.setPartnerMemberRef(result.memberRef() != null ? result.memberRef() : memberRef);
        c.setPartnerPlan(result.plan());
        return complete(c, result.memberName());
    }

    private StartCheckinResponse complete(Checkin c, String memberName) {
        c.setStatus(CheckinStatus.COMPLETED);
        c.setCompletedAt(Instant.now());
        repository.save(c);
        return new StartCheckinResponse(
                c.getId(), c.getProvider().name(), c.getStatus().name(), true, memberName, null);
    }

    /**
     * Callback do provedor, para os casos em que a confirmação chega depois em
     * vez de na resposta da validação.
     *
     * Chega sem JWT e sem header de tenant, então o tenant sai do próprio
     * registro: providerRef é um UUID único global.
     */
    @Transactional
    public void providerCallback(String providedSecret, String providerRef, boolean approved) {
        if (!secretMatches(providedSecret))
            throw new SecurityException("Invalid provider secret");

        var c = repository.findByProviderRef(providerRef)
                .orElseThrow(() -> new IllegalArgumentException("Unknown providerRef"));

        if (approved) {
            c.setStatus(CheckinStatus.COMPLETED);
            c.setCompletedAt(Instant.now());
        } else {
            c.setStatus(CheckinStatus.FAILED);
        }
        repository.save(c);
    }

    public List<CheckinItem> myHistory() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantIdOrderByStartedAtDesc(currentUser().getId(), tenantId)
                .stream()
                .map(CheckinItem::from)
                .toList();
    }

    /** Comparação em tempo constante: o secret não deve vazar por timing. */
    private boolean secretMatches(String provided) {
        if (provided == null || callbackSecret == null) return false;
        return MessageDigest.isEqual(
                callbackSecret.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
