package com.gymsystem.payments;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Billing", description = "Controle manual do scheduler de cobrança")
public class AdminBillingController {

    private final BillingScheduler scheduler;

    @Operation(summary = "Dispara o ciclo de cobrança manualmente (ADMIN only)")
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void triggerBilling() {
        log.warn("[AdminBilling] Manual billing run triggered");
        scheduler.run();
    }
}
