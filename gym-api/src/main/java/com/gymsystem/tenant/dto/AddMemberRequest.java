package com.gymsystem.tenant.dto;

import com.gymsystem.tenant.TenantRole;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AddMemberRequest {

    @Email
    @NotBlank
    private String email;

    @NotNull
    private TenantRole role;
}
