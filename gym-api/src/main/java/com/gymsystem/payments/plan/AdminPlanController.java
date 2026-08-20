package com.gymsystem.payments.plan;

import com.gymsystem.payments.plan.dto.CreatePlanRequest;
import com.gymsystem.payments.plan.dto.PlanResponse;
import com.gymsystem.payments.plan.dto.UpdatePlanRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD dos planos de mensalidade. */
@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_MANAGER', 'ROLE_ADMIN_APP', 'ROLE_ADMIN_WEB')")
@Tag(name = "Admin — Planos", description = "Catálogo de planos da academia")
public class AdminPlanController {

    private final MembershipPlanService service;

    @Operation(summary = "Lista todos os planos, inclusive os inativos")
    @GetMapping
    public ResponseEntity<List<PlanResponse>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @Operation(summary = "Cria um plano")
    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @Operation(summary = "Atualiza um plano")
    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdatePlanRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @Operation(summary = "Desativa um plano (não apaga: assinaturas existentes seguem apontando para ele)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
