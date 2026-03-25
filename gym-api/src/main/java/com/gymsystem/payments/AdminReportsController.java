package com.gymsystem.payments;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_MANAGER', 'ROLE_ADMIN_APP', 'ROLE_ADMIN_WEB')")
@Tag(name = "Admin — Relatórios", description = "Receita e churn por mês")
public class AdminReportsController {

    private final AdminReportsService service;

    @Operation(summary = "Receita total por mês (pagamentos PAID)")
    @GetMapping("/revenue")
    public List<AdminReportsService.MonthlyRevenue> revenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.revenueByMonth(from, to);
    }

    @Operation(summary = "Cancelamentos (churn) por mês")
    @GetMapping("/churn")
    public List<AdminReportsService.MonthlyChurn> churn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.churnByMonth(from, to);
    }
}
