// src/main/java/com/gymsystem/checkin/dto/StartCheckinRequest.java
package com.gymsystem.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Client request to start a check-in flow. */
@Data
public class StartCheckinRequest {

    /** WELLHUB | TOTALPASS | DIRECT. "GYMPASS" ainda e aceito como WELLHUB. */
    @NotBlank
    private String provider;

    /**
     * Codigo apresentado pelo aluno, gerado no app do parceiro.
     * Obrigatorio para WELLHUB e TOTALPASS, ignorado em DIRECT.
     */
    private String code;

    /** Only used when provider == DIRECT */
    private String gymName;
}
