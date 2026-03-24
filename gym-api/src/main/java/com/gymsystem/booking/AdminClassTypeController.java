package com.gymsystem.booking;

import com.gymsystem.booking.dto.CreateClassTypeRequest;
import com.gymsystem.booking.dto.UpdateClassTypeRequest;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/classes/types")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Class Types", description = "CRUD for class types (e.g. Yoga, CrossFit)")
public class AdminClassTypeController {

    private final ClassTypeRepository repo;
    private final TenantRepository tenantRepository;

    @Operation(summary = "Create a new class type")
    @PostMapping
    public ResponseEntity<ClassType> create(@Valid @RequestBody CreateClassTypeRequest req) {
        String code = req.getCode().trim().toUpperCase();

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new IllegalStateException("Tenant context not set");

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        if (repo.findByCodeAndActiveTrueAndTenantId(code, tenantId).isPresent()) {
            throw new IllegalArgumentException("Class type code already exists: " + code);
        }

        ClassType ct = ClassType.builder()
                .code(code)
                .name(req.getName().trim())
                .description(req.getDescription())
                .active(req.getActive() == null ? true : req.getActive())
                .tenant(tenant)
                .build();

        return ResponseEntity.ok(repo.save(ct));
    }

    @Operation(summary = "List all active class types for the current tenant")
    @GetMapping
    public ResponseEntity<List<ClassType>> listAll() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return ResponseEntity.ok(repo.findAll());
        return ResponseEntity.ok(repo.findByTenantIdAndActiveTrue(tenantId));
    }

    @Operation(summary = "Update an existing class type")
    @PutMapping("/{id}")
    public ResponseEntity<ClassType> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateClassTypeRequest req) {
        ClassType ct = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Class type not found"));
        if (req.getName() != null) ct.setName(req.getName());
        if (req.getDescription() != null) ct.setDescription(req.getDescription());
        if (req.getActive() != null) ct.setActive(req.getActive());
        return ResponseEntity.ok(repo.save(ct));
    }
}