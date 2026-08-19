package com.gymsystem.payments.plan.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreatePlanRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    private String description;

    /** Em centavos. Zero e permitido para plano cortesia. */
    @NotNull
    @Min(0)
    private Long priceCents;

    @NotNull
    @Min(1)
    @Max(60)
    private Integer intervalMonths;

    private String currency = "BRL";

    private Boolean active;

    private Integer sortOrder;
}
