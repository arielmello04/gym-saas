package com.gymsystem.tenant.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateTenantRequest {

    @NotBlank
    @Size(min = 3, max = 64)
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must be lowercase letters, numbers and hyphens only")
    private String slug;

    @NotBlank
    @Size(max = 128)
    private String name;
}
