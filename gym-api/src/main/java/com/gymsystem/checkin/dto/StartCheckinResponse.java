// src/main/java/com/gymsystem/checkin/dto/StartCheckinResponse.java
package com.gymsystem.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Resultado do check-in.
 *
 * Antes esta resposta devolvia um redirectUrl para um app externo, de quando a
 * validacao no parceiro era um stub. Wellhub e TotalPass validam o codigo de
 * forma sincrona, entao a resposta ja diz se a entrada foi liberada.
 */
@Data
@AllArgsConstructor
public class StartCheckinResponse {

    private Long    checkinId;
    private String  provider;    // WELLHUB | TOTALPASS | DIRECT
    private String  status;      // COMPLETED | FAILED
    private boolean approved;

    /** Nome do aluno como o parceiro conhece, quando ele devolve. */
    private String  memberName;

    /** Motivo da recusa. Nulo quando aprovado. */
    private String  message;
}
