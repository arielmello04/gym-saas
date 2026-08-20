// src/main/java/com/gymsystem/checkin/partner/PushPartner.java
package com.gymsystem.checkin.partner;

/**
 * Parceiro que avisa a academia por webhook (TotalPass).
 *
 * O aluno faz check-in no app, a TotalPass chama a nossa URL com os dados e um
 * link exclusivo, e a entrada só é liberada quando fazemos POST nesse link.
 */
public interface PushPartner extends CheckinPartner {

    /**
     * Confirma no parceiro a entrada de um check-in recebido por webhook.
     *
     * @param credentials credencial da academia (TotalPass: a place_api_key dela)
     * @param confirmUrl  link exclusivo que veio no payload (campo "endpoint")
     */
    PartnerValidation confirm(PartnerCredentials credentials, String confirmUrl);

    /**
     * Registra no parceiro a URL que recebe os webhooks desta academia.
     * Idempotente: chamar de novo com a mesma URL não deve quebrar.
     */
    boolean registerWebhook(PartnerCredentials credentials, String callbackUrl);
}
