// src/main/java/com/gymsystem/booking/dto/UpdateClassTypeRequest.java
package com.gymsystem.booking.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin payload to update mutable fields of a class type. */
@Data
public class UpdateClassTypeRequest {
    @Size(max = 128)
    private String name;

    @Size(max = 1000)
    private String description;

    private Boolean active;
}
