// src/main/java/com/gymsystem/checkin/partner/PartnerHealth.java
package com.gymsystem.checkin.partner;

/**
 * Resultado de um teste de conexão com o parceiro.
 *
 * Existe para a academia descobrir que a credencial está errada ANTES de um
 * aluno estar parado na recepção. Sem isso, a primeira notícia de configuração
 * torta é uma entrada recusada no balcão.
 *
 * @param provider  qual parceiro
 * @param mode      mock ou live
 * @param configured se há credencial preenchida
 * @param reachable se o servidor do parceiro respondeu
 * @param credentialsValid se ele aceitou nossa credencial
 * @param detail    o que fazer a seguir, em português
 */
public record PartnerHealth(
        String provider,
        String mode,
        boolean configured,
        boolean reachable,
        boolean credentialsValid,
        String detail
) {

    public static PartnerHealth mock(String provider) {
        return new PartnerHealth(provider, "mock", true, true, true,
                "Modo mock: valida sem chamar o parceiro. Troque para live quando tiver a credencial.");
    }

    public static PartnerHealth naoConfigurado(String provider, String faltando) {
        return new PartnerHealth(provider, "live", false, false, false,
                "Falta configurar: " + faltando);
    }

    public static PartnerHealth ok(String provider, String detalhe) {
        return new PartnerHealth(provider, "live", true, true, true, detalhe);
    }

    public static PartnerHealth credencialRecusada(String provider, String detalhe) {
        return new PartnerHealth(provider, "live", true, true, false, detalhe);
    }

    public static PartnerHealth inacessivel(String provider, String detalhe) {
        return new PartnerHealth(provider, "live", true, false, false, detalhe);
    }
}
