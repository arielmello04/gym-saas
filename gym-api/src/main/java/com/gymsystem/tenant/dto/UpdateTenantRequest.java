package com.gymsystem.tenant.dto;

import com.gymsystem.tenant.TenantPlan;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTenantRequest {

    @Size(max = 128)
    private String name;

    private TenantPlan plan;
}
