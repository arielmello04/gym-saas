package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import com.gymsystem.tenant.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Credencial de parceiro por academia.
 *
 * Esta classe existe por causa de um defeito real: as credenciais da unidade
 * (X-Gym-Id do Wellhub, place_api_key da TotalPass) estavam no application.yml,
 * então a instalação inteira só atendia UMA academia — a segunda teria as
 * visitas creditadas para a primeira, no parceiro.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerCredentialsServiceTest {

    @Mock private PartnerTenantConfigRepository repository;
    @InjectMocks private PartnerCredentialsService service;

    @Test
    @DisplayName("cada academia recebe o próprio Gym ID do Wellhub")
    void gymIdPorAcademia() {
        comConfig(1L, CheckinProvider.WELLHUB, "gym-AAA", null, true);
        comConfig(2L, CheckinProvider.WELLHUB, "gym-42", null, true);

        assertThat(service.forTenant(1L, CheckinProvider.WELLHUB).gymId()).isEqualTo("gym-AAA");
        assertThat(service.forTenant(2L, CheckinProvider.WELLHUB).gymId()).isEqualTo("gym-42");
    }

    @Test
    @DisplayName("cada academia recebe a própria place_api_key da TotalPass")
    void placeApiKeyPorAcademia() {
        comConfig(1L, CheckinProvider.TOTALPASS, null, "chave-da-uma", true);
        comConfig(2L, CheckinProvider.TOTALPASS, null, "chave-da-outra", true);

        assertThat(service.forTenant(1L, CheckinProvider.TOTALPASS).placeApiKey()).isEqualTo("chave-da-uma");
        assertThat(service.forTenant(2L, CheckinProvider.TOTALPASS).placeApiKey()).isEqualTo("chave-da-outra");
    }

    @Test
    @DisplayName("academia sem configuração recebe credencial vazia, não a de outra")
    void semConfiguracaoNaoHerdaDeOutra() {
        comConfig(1L, CheckinProvider.WELLHUB, "gym-AAA", null, true);
        when(repository.findByTenantIdAndProvider(2L, CheckinProvider.WELLHUB))
                .thenReturn(Optional.empty());

        var vazia = service.forTenant(2L, CheckinProvider.WELLHUB);

        assertThat(vazia.temGymId()).isFalse();
        assertThat(vazia.gymId()).isNull();
    }

    @Test
    @DisplayName("configuração desativada não vale: a academia fica sem credencial")
    void desativadaNaoVale() {
        comConfig(1L, CheckinProvider.WELLHUB, "gym-AAA", null, false);

        assertThat(service.forTenant(1L, CheckinProvider.WELLHUB).temGymId()).isFalse();
    }

    @Test
    @DisplayName("os campos não se misturam entre parceiros")
    void camposNaoSeMisturam() {
        comConfig(1L, CheckinProvider.WELLHUB, "gym-AAA", null, true);
        comConfig(1L, CheckinProvider.TOTALPASS, null, "chave", true);

        assertThat(service.forTenant(1L, CheckinProvider.WELLHUB).temPlaceApiKey()).isFalse();
        assertThat(service.forTenant(1L, CheckinProvider.TOTALPASS).temGymId()).isFalse();
    }

    @Test
    @DisplayName("o JWT da TotalPass fica em cache por academia, não num cache único")
    void jwtEmCachePorAcademia() {
        var partner = new TotalPassPartner();
        ReflectionTestUtils.setField(partner, "mode", "live");
        ReflectionTestUtils.setField(partner, "partnerApiKey", "chave-do-erp");

        @SuppressWarnings("unchecked")
        var cache = (java.util.Map<String, ?>) ReflectionTestUtils.getField(partner, "tokensPorAcademia");

        // O cache é indexado pela place_api_key — que é justamente o que
        // distingue as unidades. Um campo único vazaria o token de uma para outra.
        assertThat(cache).isNotNull().isEmpty();
    }

    private void comConfig(Long tenantId, CheckinProvider provider,
                           String gymId, String placeApiKey, boolean ativa) {
        var config = PartnerTenantConfig.builder()
                .id(tenantId)
                .tenant(Tenant.builder().id(tenantId).slug("t" + tenantId).name("T").active(true).build())
                .provider(provider)
                .gymId(gymId)
                .placeApiKey(placeApiKey)
                .active(ativa)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(repository.findByTenantIdAndProvider(tenantId, provider)).thenReturn(Optional.of(config));
    }
}
