package com.gymsystem.payments.plan;

import com.gymsystem.payments.plan.dto.PlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catalogo de planos da academia, visivel para o aluno. */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Planos de mensalidade da academia")
public class PlanController {

    private final MembershipPlanService service;

    @Operation(summary = "Lista os planos ativos da academia")
    @GetMapping
    public ResponseEntity<List<PlanResponse>> list() {
        return ResponseEntity.ok(service.listActive());
    }
}
