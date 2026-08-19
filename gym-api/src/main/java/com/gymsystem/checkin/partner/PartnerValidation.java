// src/main/java/com/gymsystem/checkin/partner/PartnerValidation.java
package com.gymsystem.checkin.partner;

/**
 * Resultado de uma validação ou confirmação no parceiro.
 *
 * @param approved  se o parceiro autorizou a entrada
 * @param memberRef identificador do aluno no parceiro (usado na conciliação)
 * @param memberName nome do aluno, quando o parceiro devolve
 * @param plan      produto ou plano do aluno no parceiro
 * @param reason    motivo da recusa, ou de uma falha técnica
 */
public record PartnerValidation(
        boolean approved,
        String memberRef,
        String memberName,
        String plan,
        String reason
) {

    public static PartnerValidation approved(String memberRef, String memberName, String plan) {
        return new PartnerValidation(true, memberRef, memberName, plan, null);
    }

    public static PartnerValidation denied(String reason) {
        return new PartnerValidation(false, null, null, null, reason);
    }
}
