
package com.gymsystem.auth.invite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Admin request to create a signup token. */
@Data
public class CreateTokenRequest {
    @Min(1) @Max(365)
    private Integer expiresInDays;

    @Min(1) @Max(1000)
    private Integer maxUses;
}
