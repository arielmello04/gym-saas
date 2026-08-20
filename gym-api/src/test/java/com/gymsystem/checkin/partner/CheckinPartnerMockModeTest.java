package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comportamento dos parceiros em modo mock — o padrão até existirem credenciais.
 *
 * Os dois trabalham de formas opostas, e os testes refletem isso: o Wellhub é
 * consultado com o identificador do aluno; a TotalPass avisa por webhook e nós
 * confirmamos num link.
 */
class CheckinPartnerMockModeTest {

    /** Credencial da academia; a da integradora fica nos campos do adaptador. */
    private static final PartnerCredentials CRED_WELLHUB = PartnerCredentials.wellhub("gym-42");
    private static final PartnerCredentials CRED_TP = PartnerCredentials.totalpass("place-key-da-academia");

    @Nested
    @DisplayName("Wellhub (consulta)")
    class Wellhub {

        private WellhubPartner partner;

        @BeforeEach
        void setUp() {
            partner = new WellhubPartner();
            ReflectionTestUtils.setField(partner, "mode", "mock");
            ReflectionTestUtils.setField(partner, "token", "");
        }

        @Test
        @DisplayName("atende o provider WELLHUB")
        void identificaProvider() {
            assertThat(partner.provider()).isEqualTo(CheckinProvider.WELLHUB);
        }

        @Test
        @DisplayName("em mock se considera configurado, mesmo sem credencial")
        void configuradoEmMock() {
            assertThat(partner.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("libera a entrada para um Wellhub ID conhecido")
        void aprovaIdComum() {
            var resultado = partner.validate(CRED_WELLHUB, "1234567890123", null);

            assertThat(resultado.approved()).isTrue();
            assertThat(resultado.memberRef()).isEqualTo("1234567890123");
            assertThat(resultado.reason()).isNull();
        }

        @Test
        @DisplayName("ID começando com DENY é recusado, para exercitar o caminho de erro")
        void recusaPrefixoDeny() {
            var resultado = partner.validate(CRED_WELLHUB, "DENY-001", null);

            assertThat(resultado.approved()).isFalse();
            assertThat(resultado.reason()).isNotBlank();
        }

        @Test
        @DisplayName("aluno sem Wellhub ID é recusado antes de qualquer chamada")
        void recusaSemIdentificador() {
            assertThat(partner.validate(CRED_WELLHUB, null, null).approved()).isFalse();
            assertThat(partner.validate(CRED_WELLHUB, "   ", null).approved()).isFalse();
            assertThat(partner.validate(CRED_WELLHUB, null, null).reason()).contains("sem Wellhub ID");
        }

        @Test
        @DisplayName("em live sem token: não se diz configurado e recusa com motivo claro")
        void liveSemCredencial() {
            ReflectionTestUtils.setField(partner, "mode", "live");

            assertThat(partner.isConfigured()).isFalse();
            assertThat(partner.validate(CRED_WELLHUB, "1234567890123", null).reason()).contains("nao configurado");
        }

        @Test
        @DisplayName("o custom_code é aceito junto da validação")
        void aceitaCustomCode() {
            assertThat(partner.validate(CRED_WELLHUB, "1234567890123", "PIN4242").approved()).isTrue();
            assertThat(partner.upsertCustomCode(CRED_WELLHUB, "1234567890123", "PIN4242")).isTrue();
        }
    }

    @Nested
    @DisplayName("TotalPass (webhook)")
    class TotalPass {

        private TotalPassPartner partner;

        @BeforeEach
        void setUp() {
            partner = new TotalPassPartner();
            ReflectionTestUtils.setField(partner, "mode", "mock");
            ReflectionTestUtils.setField(partner, "partnerApiKey", "");
        }

        @Test
        @DisplayName("atende o provider TOTALPASS")
        void identificaProvider() {
            assertThat(partner.provider()).isEqualTo(CheckinProvider.TOTALPASS);
        }

        @Test
        @DisplayName("confirma a entrada no link recebido")
        void confirmaEntrada() {
            var resultado = partner.confirm(CRED_TP, "https://parceiro/webhook_confirmations/tok-1");

            assertThat(resultado.approved()).isTrue();
        }

        @Test
        @DisplayName("sem link de confirmação, recusa em vez de chamar qualquer coisa")
        void recusaSemLink() {
            assertThat(partner.confirm(CRED_TP, null).approved()).isFalse();
            assertThat(partner.confirm(CRED_TP, "  ").reason()).contains("sem link");
        }

        @Test
        @DisplayName("link marcado com DENY simula o check-in já validado no portal")
        void recusaJaValidado() {
            var resultado = partner.confirm(CRED_TP, "https://parceiro/webhook_confirmations/DENY-1");

            assertThat(resultado.approved()).isFalse();
            assertThat(resultado.reason()).contains("ja validado");
        }

        @Test
        @DisplayName("registra a URL de webhook")
        void registraWebhook() {
            assertThat(partner.registerWebhook(CRED_TP, "https://academia/webhook")).isTrue();
        }

        @Test
        @DisplayName("em live sem chaves: não se diz configurado e recusa com motivo claro")
        void liveSemCredencial() {
            ReflectionTestUtils.setField(partner, "mode", "live");

            assertThat(partner.isConfigured()).isFalse();
            assertThat(partner.confirm(CRED_TP, "https://x/tok").reason()).contains("nao configurado");
            assertThat(partner.registerWebhook(CRED_TP, "https://academia/webhook")).isFalse();
        }
    }

    @Nested
    @DisplayName("Diagnóstico de conexão")
    class Diagnostico {

        @Test
        @DisplayName("em mock, avisa que não chama o parceiro e diz o que fazer a seguir")
        void mockAvisaOModo() {
            var wellhub = new WellhubPartner();
            ReflectionTestUtils.setField(wellhub, "mode", "mock");

            var saude = wellhub.check(CRED_WELLHUB);

            assertThat(saude.mode()).isEqualTo("mock");
            assertThat(saude.configured()).isTrue();
            assertThat(saude.detail()).contains("live");
        }

        @Test
        @DisplayName("em live sem token da integradora, nomeia a variável que falta")
        void nomeiaVariavelFaltando() {
            var wellhub = new WellhubPartner();
            ReflectionTestUtils.setField(wellhub, "mode", "live");
            ReflectionTestUtils.setField(wellhub, "token", "");

            var saude = wellhub.check(CRED_WELLHUB);

            assertThat(saude.configured()).isFalse();
            assertThat(saude.detail()).contains("WELLHUB_TOKEN");
        }

        @Test
        @DisplayName("separa o que falta da integradora do que falta da academia")
        void nomeiaAsDuas() {
            var tp = new TotalPassPartner();
            ReflectionTestUtils.setField(tp, "mode", "live");
            ReflectionTestUtils.setField(tp, "partnerApiKey", "");

            assertThat(tp.check(CRED_TP).detail()).contains("TOTALPASS_PARTNER_API_KEY");

            // Sem a chave da academia, a falta apontada é outra.
            var semAcademia = new TotalPassPartner();
            ReflectionTestUtils.setField(semAcademia, "mode", "live");
            ReflectionTestUtils.setField(semAcademia, "partnerApiKey", "chave-do-erp");
            assertThat(semAcademia.check(PartnerCredentials.vazia()).detail())
                    .contains("place_api_key desta academia");
        }

        @Test
        @DisplayName("servidor inalcançável é reportado como tal, não como credencial errada")
        void servidorInalcancavel() {
            var wellhub = new WellhubPartner();
            ReflectionTestUtils.setField(wellhub, "mode", "live");
            ReflectionTestUtils.setField(wellhub, "token", "t");
            // Porta fechada de propósito.
            ReflectionTestUtils.setField(wellhub, "baseUrl", "http://127.0.0.1:9/access/v1");

            var saude = wellhub.check(CRED_WELLHUB);

            assertThat(saude.reachable()).isFalse();
            assertThat(saude.credentialsValid()).isFalse();
            assertThat(saude.detail()).contains("alcancar");
        }
    }

    @Nested
    @DisplayName("Registro de parceiros")
    class Registro {

        @Test
        @DisplayName("cada provider é resolvido pelo tipo de integração que tem")
        void resolvePorTipo() {
            var registry = registryComOsDois();

            assertThat(registry.requireValidating(CheckinProvider.WELLHUB).provider())
                    .isEqualTo(CheckinProvider.WELLHUB);
            assertThat(registry.requirePush(CheckinProvider.TOTALPASS).provider())
                    .isEqualTo(CheckinProvider.TOTALPASS);
        }

        @Test
        @DisplayName("pedir o tipo errado dá erro explícito, não uma chamada sem sentido")
        void tipoErrado() {
            var registry = registryComOsDois();

            assertThatThrownBy(() -> registry.requirePush(CheckinProvider.WELLHUB))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("webhook");

            assertThatThrownBy(() -> registry.requireValidating(CheckinProvider.TOTALPASS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("consulta");
        }

        @Test
        @DisplayName("DIRECT não tem integração externa")
        void directNaoTemIntegracao() {
            assertThatThrownBy(() -> registryComOsDois().requireValidating(CheckinProvider.DIRECT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("parceiro sem configuração dá erro explícito")
        void parceiroNaoConfigurado() {
            var wellhub = new WellhubPartner();
            ReflectionTestUtils.setField(wellhub, "mode", "live");
            ReflectionTestUtils.setField(wellhub, "token", "");

            var registry = new CheckinPartnerRegistry(List.of(wellhub));
            registry.index();

            assertThatThrownBy(() -> registry.requireValidating(CheckinProvider.WELLHUB))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nao esta configurado");
        }

        @Test
        @DisplayName("os parceiros de webhook ficam listados para registrar a URL")
        void listaParceirosDeWebhook() {
            assertThat(registryComOsDois().pushPartners())
                    .singleElement()
                    .extracting(PushPartner::provider)
                    .isEqualTo(CheckinProvider.TOTALPASS);
        }

        private CheckinPartnerRegistry registryComOsDois() {
            var wellhub = new WellhubPartner();
            ReflectionTestUtils.setField(wellhub, "mode", "mock");
            var totalpass = new TotalPassPartner();
            ReflectionTestUtils.setField(totalpass, "mode", "mock");

            var registry = new CheckinPartnerRegistry(List.of(wellhub, totalpass));
            registry.index();
            return registry;
        }
    }
}
