// src/main/java/com/gymsystem/booking/ClassTypePublicController.java
package com.gymsystem.booking;

import com.gymsystem.booking.dto.ClassTypeResponse;
import com.gymsystem.tenant.context.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public endpoint to list active class types for filters and catalog.
 *
 * Public but still tenant-scoped: the caller must identify the gym through
 * X-Tenant-ID or the subdomain. It used to call findAll(), which handed every
 * gym's catalog to any anonymous request.
 */
@RestController
@RequestMapping("/api/v1/classes/types")
@RequiredArgsConstructor
public class ClassTypePublicController {

    private final ClassTypeRepository repo;

    @Operation(summary = "Public list of active class types for the requested gym")
    @GetMapping
    public ResponseEntity<List<ClassTypeResponse>> listActive() {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(
                repo.findByTenantIdAndActiveTrue(tenantId).stream()
                        .map(ClassTypeResponse::from)
                        .toList()
        );
    }
}
