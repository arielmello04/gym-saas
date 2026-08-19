// src/main/java/com/gymsystem/checkin/partner/CheckinPartner.java
package com.gymsystem.checkin.partner;

import com.gymsystem.checkin.CheckinProvider;

/**
 * Base das integrações de check-in.
 *
 * Wellhub e TotalPass resolvem o mesmo problema por caminhos opostos, e por isso
 * as capacidades ficam em interfaces separadas em vez de um método só que
 * significa coisas diferentes conforme o parceiro:
 *
 *   Wellhub  → {@link ValidatingPartner}: nós perguntamos. O aluno faz check-in
 *              no app e a academia chama a API com o identificador dele.
 *   TotalPass→ {@link PushPartner}: eles avisam. A TotalPass chama a nossa URL
 *              quando o aluno faz check-in, e nós confirmamos no link recebido.
 *
 * Toda operação recebe {@link PartnerCredentials} porque o adaptador é um bean
 * único atendendo todas as academias: a credencial da unidade não pode morar
 * nele.
 */
public interface CheckinPartner {

    CheckinProvider provider();

    /**
     * Se a integradora tem credencial configurada (token do Wellhub,
     * partner_api_key da TotalPass). É a metade global; a outra metade vem da
     * academia, em {@link PartnerCredentials}.
     */
    boolean isConfigured();

    /**
     * Testa conexão e credencial contra o parceiro, sem efeito colateral.
     *
     * É o que separa "achamos que está configurado" de "o parceiro respondeu e
     * aceitou nossa chave".
     */
    PartnerHealth check(PartnerCredentials credentials);
}
