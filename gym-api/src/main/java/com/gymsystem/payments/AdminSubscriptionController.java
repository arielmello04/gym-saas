package com.gymsystem.payments;

import com.gymsystem.payments.dto.AdminSubscriptionItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Assinaturas da academia, para o painel administrativo. */
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_MANAGER', 'ROLE_ADMIN_APP', 'ROLE_ADMIN_WEB')")
@Tag(name = "Admin — Assinaturas", description = "Assinaturas dos alunos da academia")
public class AdminSubscriptionController {

    private final AdminSubscriptionService service;

    @Operation(summary = "Lista as assinaturas da academia, da mais recente para a mais antiga")
    @GetMapping
    public ResponseEntity<List<AdminSubscriptionItem>> list() {
        return ResponseEntity.ok(service.list());
    }
}
