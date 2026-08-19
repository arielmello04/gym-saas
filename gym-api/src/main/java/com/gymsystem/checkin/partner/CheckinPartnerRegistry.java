// src/main/java/com/gymsystem/checkin/partner/CheckinPartnerRegistry.java
package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda as integrações de check-in disponíveis, indexadas pelo provider.
 *
 * Todas ficam registradas ao mesmo tempo de propósito: uma academia costuma
 * atender Wellhub e TotalPass simultaneamente, então a escolha é por
 * requisição, não por configuração global do ambiente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckinPartnerRegistry {

    private final List<CheckinPartner> partners;
    private final Map<CheckinProvider, CheckinPartner> byProvider = new EnumMap<>(CheckinProvider.class);

    @PostConstruct
    void index() {
        for (CheckinPartner p : partners) {
            byProvider.put(p.provider(), p);
            log.info("[Check-in] Parceiro registrado: {} ({}, configurado={})",
                    p.provider(), tipo(p), p.isConfigured());
        }
    }

    /** Parceiro que a academia consulta (Wellhub). */
    public ValidatingPartner requireValidating(CheckinProvider provider) {
        CheckinPartner partner = require(provider);
        if (!(partner instanceof ValidatingPartner v)) {
            throw new IllegalArgumentException(
                    provider + " nao valida por consulta; a entrada chega por webhook");
        }
        return v;
    }

    /** Parceiro que avisa por webhook (TotalPass). */
    public PushPartner requirePush(CheckinProvider provider) {
        CheckinPartner partner = require(provider);
        if (!(partner instanceof PushPartner p)) {
            throw new IllegalArgumentException(
                    provider + " nao envia webhook; a entrada e validada por consulta");
        }
        return p;
    }

    /**
     * Diagnóstico de todos os parceiros para uma academia.
     *
     * A credencial é resolvida por parceiro porque metade dela é da academia:
     * o mesmo servidor pode estar saudável para uma unidade e sem configuração
     * para outra.
     */
    public List<PartnerHealth> health(java.util.function.Function<CheckinProvider, PartnerCredentials> credenciais) {
        return partners.stream()
                .map(p -> p.check(credenciais.apply(p.provider())))
                .toList();
    }

    /** Todos os parceiros que avisam por webhook, para registrar a URL no start. */
    public List<PushPartner> pushPartners() {
        return partners.stream()
                .filter(PushPartner.class::isInstance)
                .map(PushPartner.class::cast)
                .toList();
    }

    private CheckinPartner require(CheckinProvider provider) {
        CheckinPartner partner = byProvider.get(provider);
        if (partner == null) {
            throw new IllegalArgumentException("Sem integracao de check-in para " + provider);
        }
        if (!partner.isConfigured()) {
            throw new IllegalStateException(provider + " nao esta configurado nesta instalacao");
        }
        return partner;
    }

    private String tipo(CheckinPartner p) {
        return p instanceof PushPartner ? "webhook" : "consulta";
    }
}
