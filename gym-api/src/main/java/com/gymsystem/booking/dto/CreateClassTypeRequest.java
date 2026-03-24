// src/main/java/com/gymsystem/booking/dto/CreateClassTypeRequest.java
package com.gymsystem.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin payload to create a class type. */
@Data
public class CreateClassTypeRequest {
    @NotBlank @Size(max = 64)
    private String code; // e.g. "PILATES", uppercase expected

    @NotBlank @Size(max = 128)
    private String name;

    @Size(max = 1000)
    private String description;

    private Boolean active; // default true if null
}
