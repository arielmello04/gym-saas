// src/main/java/com/gymsystem/checkin/partner/PartnerCredentials.java
package com.gymsystem.checkin.partner;

/**
 * Credencial da academia no parceiro, resolvida por requisição.
 *
 * Passa como argumento em vez de ficar em campo `@Value` do adaptador porque o
 * adaptador é um único bean atendendo todas as academias da instalação — com a
 * credencial fixa nele, a segunda academia teria as visitas creditadas para a
 * primeira.
 *
 * @param gymId       Wellhub: header X-Gym-Id da unidade
 * @param placeApiKey TotalPass: chave da unidade, gerada no portal da academia
 */
public record PartnerCredentials(String gymId, String placeApiKey) {

    public static PartnerCredentials wellhub(String gymId) {
        return new PartnerCredentials(gymId, null);
    }

    public static PartnerCredentials totalpass(String placeApiKey) {
        return new PartnerCredentials(null, placeApiKey);
    }

    /** Usada no modo mock e nos diagnósticos de academia sem configuração. */
    public static PartnerCredentials vazia() {
        return new PartnerCredentials(null, null);
    }

    public boolean temGymId()       { return gymId != null && !gymId.isBlank(); }
    public boolean temPlaceApiKey() { return placeApiKey != null && !placeApiKey.isBlank(); }
}
