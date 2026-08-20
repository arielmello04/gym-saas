package com.gymsystem.booking;

import com.gymsystem.booking.dto.ClassTypeResponse;
import com.gymsystem.booking.dto.CreateClassTypeRequest;
import com.gymsystem.booking.dto.UpdateClassTypeRequest;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
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
    public ResponseEntity<ClassTypeResponse> create(@Valid @RequestBody CreateClassTypeRequest req) {
        String code = req.getCode().trim().toUpperCase();
        Long tenantId = TenantGuard.currentTenantId();

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        if (repo.existsByCodeAndTenantId(code, tenantId)) {
            throw new IllegalArgumentException("Class type code already exists: " + code);
        }

        ClassType ct = ClassType.builder()
                .code(code)
                .name(req.getName().trim())
                .description(req.getDescription())
                .active(req.getActive() == null ? true : req.getActive())
                .tenant(tenant)
                .build();

        return ResponseEntity.ok(ClassTypeResponse.from(repo.save(ct)));
    }

    @Operation(summary = "List all active class types for the current tenant")
    @GetMapping
    public ResponseEntity<List<ClassTypeResponse>> listAll() {
        Long tenantId = TenantGuard.currentTenantId();
        return ResponseEntity.ok(
                repo.findByTenantIdAndActiveTrue(tenantId).stream()
                        .map(ClassTypeResponse::from)
                        .toList()
        );
    }

    @Operation(summary = "Update an existing class type")
    @PutMapping("/{id}")
    public ResponseEntity<ClassTypeResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateClassTypeRequest req) {
        Long tenantId = TenantGuard.currentTenantId();

        // Busca escopada por tenant: com findById puro, o admin de uma academia
        // editava o tipo de aula de outra so acertando o id.
        ClassType ct = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Class type not found"));

        if (req.getName() != null) ct.setName(req.getName());
        if (req.getDescription() != null) ct.setDescription(req.getDescription());
        if (req.getActive() != null) ct.setActive(req.getActive());
        return ResponseEntity.ok(ClassTypeResponse.from(repo.save(ct)));
    }
}
