// src/main/java/com/gymsystem/booking/dto/ClassTypeResponse.java
package com.gymsystem.booking.dto;

import com.gymsystem.booking.ClassType;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Class type as the API exposes it.
 *
 * The entity carries a lazy association to Tenant, and one of these endpoints is
 * public — returning the entity published the gym's SaaS plan to anyone.
 */
@Data
@AllArgsConstructor
public class ClassTypeResponse {

    private Long    id;
    private String  code;
    private String  name;
    private String  description;
    private boolean active;

    public static ClassTypeResponse from(ClassType ct) {
        return new ClassTypeResponse(
                ct.getId(),
                ct.getCode(),
                ct.getName(),
                ct.getDescription(),
                ct.isActive()
        );
    }
}
