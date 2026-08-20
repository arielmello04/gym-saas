package com.gymsystem.payments.plan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Campos opcionais: o que vier nulo fica como esta. */
@Data
public class UpdatePlanRequest {

    @Size(max = 128)
    private String name;

    private String description;

    @Min(0)
    private Long priceCents;

    @Min(1) @Max(60)
    private Integer intervalMonths;

    private Boolean active;

    private Integer sortOrder;
}
