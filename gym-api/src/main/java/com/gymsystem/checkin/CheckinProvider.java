// src/main/java/com/gymsystem/checkin/CheckinProvider.java
package com.gymsystem.checkin;

/** External providers supported by the app. */
public enum CheckinProvider {

    /** Wellhub — o antigo Gympass. Clientes antigos ainda mandam "GYMPASS". */
    WELLHUB,

    TOTALPASS,

    /** Aluno da propria academia, sem parceiro no meio. */
    DIRECT;

    /**
     * Aceita o nome do provider como o cliente mandar, incluindo o nome antigo.
     *
     * A troca de Gympass para Wellhub e so de marca: apps ja publicados continuam
     * enviando "GYMPASS" e precisam seguir funcionando.
     */
    public static CheckinProvider fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        String value = raw.trim().toUpperCase();
        if (value.equals("GYMPASS")) return WELLHUB;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown check-in provider: " + raw);
        }
    }

    /** True para os providers que dependem de validacao num parceiro externo. */
    public boolean isPartner() {
        return this != DIRECT;
    }
}
