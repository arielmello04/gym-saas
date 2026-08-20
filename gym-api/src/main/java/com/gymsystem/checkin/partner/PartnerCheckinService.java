package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.*;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Check-ins que o parceiro empurra por webhook (TotalPass).
 *
 * O evento chega, fica guardado como PENDING e só vira entrada liberada quando
 * confirmamos no link exclusivo que veio no payload. Guardar antes de confirmar
 * é o que permite a recepção ver quem chegou, e é o que preserva o registro
 * quando a confirmação falha.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerCheckinService {

    private final PartnerCheckinEventRepository eventRepository;
    private final PartnerMemberLinkRepository linkRepository;
    private final CheckinRepository checkinRepository;
    private final TenantRepository tenantRepository;
    private final CheckinPartnerRegistry partners;
    private final UserRepository userRepository;
    private final PartnerCredentialsService credentials;

    /**
     * Registra um check-in recebido do parceiro.
     *
     * Idempotente pelo link de confirmação: o parceiro pode reenviar a mesma
     * notificação, e ela não pode virar duas entradas.
     */
    @Transactional
    public PartnerCheckinEvent receive(Long tenantId, CheckinProvider provider, InboundCheckin inbound) {
        var existente = eventRepository.findByConfirmUrl(inbound.confirmUrl());
        if (existente.isPresent()) {
            log.info("[{}] Webhook repetido para {} - ignorando", provider, inbound.confirmUrl());
            return existente.get();
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        // Casa o aluno pelo CPF que vem no payload, quando há vínculo cadastrado.
        User aluno = linkRepository
                .findByTenantIdAndProviderAndDocument(tenantId, provider, inbound.document())
                .map(PartnerMemberLink::getUser)
                .orElse(null);

        var evento = PartnerCheckinEvent.builder()
                .tenant(tenant)
                .provider(provider)
                .confirmUrl(inbound.confirmUrl())
                .externalUser(inbound.externalUser())
                .userName(inbound.userName())
                .userDocument(inbound.document())
                .planCode(inbound.planCode())
                .placeCode(inbound.placeCode())
                .startedAt(inbound.startedAt())
                .expiresAt(inbound.expiresAt())
                .status(PartnerEventStatus.PENDING)
                .user(aluno)
                .receivedAt(Instant.now())
                .build();

        evento = eventRepository.save(evento);
        log.info("[{}] Check-in recebido de {} (aluno {}), valido ate {}",
                provider, inbound.userName(),
                aluno != null ? aluno.getEmail() : "nao vinculado", inbound.expiresAt());
        return evento;
    }

    /** Check-ins aguardando liberação na recepção. */
    public List<PartnerCheckinEvent> pending(Long tenantId) {
        return eventRepository.findByTenantIdAndStatusOrderByReceivedAtDesc(
                tenantId, PartnerEventStatus.PENDING);
    }

    /**
     * Libera a entrada: confirma no parceiro e grava o check-in do nosso lado.
     *
     * O check-in local só é criado depois do parceiro aceitar — do contrário a
     * academia teria uma entrada registrada que o parceiro não vai pagar.
     */
    @Transactional
    public PartnerCheckinEvent confirm(Long tenantId, Long eventId, String vincularEmail) {
        PartnerCheckinEvent evento = eventRepository.findByIdAndTenantId(eventId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Check-in nao encontrado: " + eventId));

        if (evento.getStatus() == PartnerEventStatus.CONFIRMED) {
            return evento; // idempotente: dois cliques na recepção não viram duas entradas
        }

        // Primeira visita: a pessoa existe no parceiro mas ainda não no nosso
        // cadastro. A recepção informa de quem é, e o vínculo passa a valer para
        // as próximas vezes. Sem isso não há a quem atribuir a entrada.
        if (evento.getUser() == null) {
            if (vincularEmail == null || vincularEmail.isBlank()) {
                throw new IllegalStateException(
                        "Aluno nao identificado: informe o e-mail para vincular o CPF "
                        + evento.getUserDocument() + " a um cadastro");
            }
            evento.setUser(vincularOuAtualizar(evento, vincularEmail.trim()));
        }

        Instant agora = Instant.now();
        if (evento.isExpired(agora)) {
            evento.setStatus(PartnerEventStatus.EXPIRED);
            evento.setFailureReason("Prazo de validacao expirado");
            return eventRepository.save(evento);
        }

        PartnerValidation resultado = partners.requirePush(evento.getProvider())
                .confirm(credentials.forTenant(tenantId, evento.getProvider()), evento.getConfirmUrl());

        if (!resultado.approved()) {
            evento.setStatus(PartnerEventStatus.FAILED);
            evento.setFailureReason(resultado.reason());
            return eventRepository.save(evento);
        }

        Checkin checkin = checkinRepository.save(Checkin.builder()
                .user(evento.getUser())
                .tenant(evento.getTenant())
                .provider(evento.getProvider())
                .providerRef("CHK-" + UUID.randomUUID())
                .partnerMemberRef(evento.getExternalUser())
                .partnerPlan(evento.getPlanCode())
                .status(CheckinStatus.COMPLETED)
                .startedAt(evento.getStartedAt() != null ? evento.getStartedAt() : agora)
                .completedAt(agora)
                .build());

        evento.setStatus(PartnerEventStatus.CONFIRMED);
        evento.setConfirmedAt(agora);
        evento.setCheckin(checkin);
        return eventRepository.save(evento);
    }

    /**
     * Cria (ou atualiza) o vínculo do aluno com o parceiro a partir dos dados
     * que vieram no webhook, para a próxima visita ser reconhecida sozinha.
     */
    private User vincularOuAtualizar(PartnerCheckinEvent evento, String email) {
        User aluno = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Aluno nao encontrado: " + email));

        Long tenantId = evento.getTenant().getId();
        var link = linkRepository
                .findByTenantIdAndUserIdAndProvider(tenantId, aluno.getId(), evento.getProvider())
                .orElseGet(() -> PartnerMemberLink.builder()
                        .tenant(evento.getTenant())
                        .user(aluno)
                        .provider(evento.getProvider())
                        .createdAt(Instant.now())
                        .build());

        if (evento.getExternalUser() != null) link.setExternalId(evento.getExternalUser());
        if (link.getExternalId() == null)     link.setExternalId(evento.getUserDocument());
        link.setDocument(evento.getUserDocument());
        link.setUpdatedAt(Instant.now());
        linkRepository.save(link);

        log.info("[{}] Aluno {} vinculado ao CPF {} na primeira visita",
                evento.getProvider(), email, evento.getUserDocument());
        return aluno;
    }

    /**
     * Fecha os check-ins que passaram do prazo do parceiro.
     *
     * Sem isso a recepção continuaria vendo entradas que o parceiro já não
     * aceita mais confirmar.
     */
    @Scheduled(fixedDelayString = "${checkin.partners.expiry-check-ms:300000}")
    @Transactional
    public void expirarPendentes() {
        List<PartnerCheckinEvent> vencidos =
                eventRepository.findByStatusAndExpiresAtBefore(PartnerEventStatus.PENDING, Instant.now());
        if (vencidos.isEmpty()) return;

        for (PartnerCheckinEvent e : vencidos) {
            e.setStatus(PartnerEventStatus.EXPIRED);
            e.setFailureReason("Prazo de validacao expirado");
            eventRepository.save(e);
        }
        log.info("[Check-in] {} check-in(s) de parceiro expiraram sem confirmacao", vencidos.size());
    }

    /** Vínculo do aluno com o parceiro, quando existe. */
    public Optional<PartnerMemberLink> link(Long tenantId, Long userId, CheckinProvider provider) {
        return linkRepository.findByTenantIdAndUserIdAndProvider(tenantId, userId, provider);
    }

    /** Dados normalizados de um check-in recebido, independentes do parceiro. */
    public record InboundCheckin(
            String confirmUrl,
            String externalUser,
            String userName,
            String document,
            String planCode,
            String placeCode,
            Instant startedAt,
            Instant expiresAt
    ) {}
}
