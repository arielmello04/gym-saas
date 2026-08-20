package com.gymsystem.payments.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacao de assinatura dos webhooks de pagamento.
 *
 * Os tres gateways (Mercado Pago, Pagar.me, Stripe) comparavam a assinatura com
 * String.equals, que vaza por timing quantos caracteres iniciais um atacante
 * acertou. A comparacao passou a ser em tempo constante e centralizada aqui.
 */
class HmacVerifierTest {

    private static final String SECRET = "whsec_um_segredo_de_teste";

    @Test
    @DisplayName("aceita a assinatura correta")
    void aceitaAssinaturaValida() {
        byte[] payload = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        String assinatura = hmacHex(SECRET, payload);

        assertThat(HmacVerifier.matchesHex(SECRET, payload, assinatura)).isTrue();
    }

    @Test
    @DisplayName("recusa assinatura gerada com outro segredo")
    void recusaSegredoErrado() {
        byte[] payload = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        String assinaturaDeOutro = hmacHex("segredo_do_atacante", payload);

        assertThat(HmacVerifier.matchesHex(SECRET, payload, assinaturaDeOutro)).isFalse();
    }

    @Test
    @DisplayName("recusa quando o corpo foi alterado depois de assinado")
    void recusaCorpoAdulterado() {
        byte[] original = "{\"amount\":100}".getBytes(StandardCharsets.UTF_8);
        byte[] adulterado = "{\"amount\":999}".getBytes(StandardCharsets.UTF_8);
        String assinatura = hmacHex(SECRET, original);

        assertThat(HmacVerifier.matchesHex(SECRET, adulterado, assinatura)).isFalse();
    }

    @Test
    @DisplayName("é indiferente a maiúsculas: os provedores divergem no formato do hex")
    void aceitaHexEmMaiusculas() {
        byte[] payload = "evento".getBytes(StandardCharsets.UTF_8);
        String assinatura = hmacHex(SECRET, payload).toUpperCase(Locale.ROOT);

        assertThat(HmacVerifier.matchesHex(SECRET, payload, assinatura)).isTrue();
    }

    @Test
    @DisplayName("aceita payload como string — é o formato do Stripe (timestamp.corpo)")
    void aceitaPayloadString() {
        String assinado = "1700000000." + "{\"id\":\"evt\"}";
        String assinatura = hmacHex(SECRET, assinado.getBytes(StandardCharsets.UTF_8));

        assertThat(HmacVerifier.matchesHex(SECRET, assinado, assinatura)).isTrue();
    }

    // ── Entradas degeneradas ──────────────────────────────────

    @Test
    @DisplayName("sem segredo configurado, nada é aceito")
    void recusaSemSegredo() {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);

        // Segredo em branco significa "webhook nao configurado": recusa sem tentar
        // calcular nada (o proprio HMAC nem aceita chave vazia).
        assertThat(HmacVerifier.matchesHex("", payload, "qualquer")).isFalse();
        assertThat(HmacVerifier.matchesHex(null, payload, "qualquer")).isFalse();
        assertThat(HmacVerifier.matchesHex("   ", payload, "qualquer")).isFalse();
    }

    @Test
    @DisplayName("assinatura ausente é recusada, não estoura")
    void recusaAssinaturaNula() {
        assertThat(HmacVerifier.matchesHex(SECRET, "x".getBytes(StandardCharsets.UTF_8), null))
                .isFalse();
    }

    @Test
    @DisplayName("assinatura de tamanho errado é recusada sem exceção")
    void recusaAssinaturaTruncada() {
        byte[] payload = "evento".getBytes(StandardCharsets.UTF_8);
        String truncada = hmacHex(SECRET, payload).substring(0, 10);

        assertThat(HmacVerifier.matchesHex(SECRET, payload, truncada)).isFalse();
    }

    @Test
    @DisplayName("o hex gerado tem o tamanho de um SHA-256 e é estável")
    void hexEstavel() {
        byte[] payload = "evento".getBytes(StandardCharsets.UTF_8);

        String primeiro = HmacVerifier.hex(SECRET, payload);
        String segundo  = HmacVerifier.hex(SECRET, payload);

        assertThat(primeiro).hasSize(64).isEqualTo(segundo);
    }

    /** HMAC calculado de forma independente, para o teste não repetir a implementação. */
    private static String hmacHex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
