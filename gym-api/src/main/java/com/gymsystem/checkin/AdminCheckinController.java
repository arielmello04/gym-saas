package com.gymsystem.checkin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Conciliacao de check-ins com Wellhub e TotalPass. */
@RestController
@RequestMapping("/api/v1/admin/checkins")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_OWNER', 'ROLE_MANAGER', 'ROLE_ADMIN_APP', 'ROLE_ADMIN_WEB')")
@Tag(name = "Admin — Check-ins", description = "Conciliação de check-ins com os parceiros")
public class AdminCheckinController {

    private final AdminCheckinService service;

    @Operation(summary = "Lista os check-ins do período, opcionalmente de um parceiro só")
    @GetMapping
    public ResponseEntity<List<AdminCheckinService.AdminCheckinItem>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String provider) {

        CheckinProvider filter = (provider == null || provider.isBlank())
                ? null
                : CheckinProvider.fromInput(provider);

        return ResponseEntity.ok(service.list(from, to, filter));
    }

    @Operation(summary = "Total de check-ins por parceiro no período (aprovados, recusados, pendentes)")
    @GetMapping("/summary")
    public ResponseEntity<List<AdminCheckinService.ProviderSummary>> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(service.summary(from, to));
    }
}
