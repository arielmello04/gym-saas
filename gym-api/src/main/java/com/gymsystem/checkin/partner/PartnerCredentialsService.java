package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolve a credencial que a academia usa em cada parceiro.
 *
 * Existe para que nenhum ponto de chamada precise lembrar de buscar a
 * configuração do tenant antes de falar com o parceiro — esquecer isso foi
 * exatamente o defeito da primeira versão, em que a credencial vinha do
 * `application.yml` e valia para a instalação inteira.
 */
@Service
@RequiredArgsConstructor
public class PartnerCredentialsService {

    private final PartnerTenantConfigRepository repository;

    /**
     * Credencial da academia no parceiro. Devolve vazia quando não há
     * configuração — o adaptador transforma isso numa recusa com motivo claro,
     * em vez de chamar o parceiro sem identificar a unidade.
     */
    public PartnerCredentials forTenant(Long tenantId, CheckinProvider provider) {
        return repository.findByTenantIdAndProvider(tenantId, provider)
                .filter(PartnerTenantConfig::isActive)
                .map(c -> switch (provider) {
                    case WELLHUB   -> PartnerCredentials.wellhub(c.getGymId());
                    case TOTALPASS -> PartnerCredentials.totalpass(c.getPlaceApiKey());
                    case DIRECT    -> PartnerCredentials.vazia();
                })
                .orElseGet(PartnerCredentials::vazia);
    }
}
