// src/main/java/com/gymsystem/booking/ClassTypePublicController.java
package com.gymsystem.booking;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public endpoint to list active class types for filters and catalog. */
@RestController
@RequestMapping("/api/v1/classes/types")
@RequiredArgsConstructor
public class ClassTypePublicController {

    private final ClassTypeRepository repo;

    @Operation(summary = "Public list of active class types")
    @GetMapping
    public ResponseEntity<List<ClassType>> listActive() {
        // NOTE: returning entity is acceptable here because it has only safe fields.
        // If you later add sensitive fields, create a DTO.
        return ResponseEntity.ok(
                repo.findAll().stream().filter(ClassType::isActive).toList()
        );
    }
}
