package com.gymsystem.checkin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckinProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {"GYMPASS", "gympass", " Gympass "})
    @DisplayName("GYMPASS continua sendo aceito: a marca virou Wellhub, mas há app publicado mandando o nome antigo")
    void aceitaNomeAntigoDoGympass(String entrada) {
        assertThat(CheckinProvider.fromInput(entrada)).isEqualTo(CheckinProvider.WELLHUB);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WELLHUB", "wellhub", "TotalPass", "direct"})
    @DisplayName("aceita os providers atuais em qualquer caixa")
    void aceitaProvidersAtuais(String entrada) {
        assertThat(CheckinProvider.fromInput(entrada)).isNotNull();
    }

    @Test
    @DisplayName("provider desconhecido é rejeitado com a entrada original na mensagem")
    void rejeitaDesconhecido() {
        assertThatThrownBy(() -> CheckinProvider.fromInput("SMARTFIT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SMARTFIT");
    }

    @Test
    @DisplayName("provider vazio ou nulo é rejeitado")
    void rejeitaVazio() {
        assertThatThrownBy(() -> CheckinProvider.fromInput(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckinProvider.fromInput("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("só WELLHUB e TOTALPASS passam por validação externa")
    void identificaQuemDependeDeParceiro() {
        assertThat(CheckinProvider.WELLHUB.isPartner()).isTrue();
        assertThat(CheckinProvider.TOTALPASS.isPartner()).isTrue();
        assertThat(CheckinProvider.DIRECT.isPartner()).isFalse();
    }
}
