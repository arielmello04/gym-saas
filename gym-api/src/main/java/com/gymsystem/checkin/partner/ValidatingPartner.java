// src/main/java/com/gymsystem/checkin/partner/ValidatingPartner.java
package com.gymsystem.checkin.partner;

/**
 * Parceiro que a academia consulta para liberar a entrada (Wellhub).
 *
 * O aluno já fez check-in no aplicativo do parceiro; aqui só confirmamos que
 * aquele check-in existe e está no prazo.
 */
public interface ValidatingPartner extends CheckinPartner {

    /**
     * @param credentials credencial da academia (Wellhub: o X-Gym-Id da unidade)
     * @param memberRef   identificador do aluno no parceiro (gympass_id)
     * @param customCode  código interno da academia a associar ao aluno, opcional
     *
     * Nunca lança por recusa do parceiro nem por falha de rede: as duas viram
     * {@link PartnerValidation#denied}, para o check-in ficar registrado como
     * recusado em vez de derrubar a requisição.
     */
    PartnerValidation validate(PartnerCredentials credentials, String memberRef, String customCode);
}
