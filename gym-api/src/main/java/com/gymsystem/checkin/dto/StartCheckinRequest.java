// src/main/java/com/gymsystem/checkin/dto/StartCheckinRequest.java
package com.gymsystem.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Client request to start a check-in flow. */
@Data
public class StartCheckinRequest {
    @NotBlank
    private String provider; // GYMPASS | TOTALPASS | DIRECT

    /** Only used when provider == DIRECT */
    private String gymName;
}
