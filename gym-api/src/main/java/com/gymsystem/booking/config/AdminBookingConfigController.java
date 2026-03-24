// src/main/java/com/gymsystem/booking/config/AdminBookingConfigController.java
package com.gymsystem.booking.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin API to view/update booking window configuration. */
@RestController
@RequestMapping("/api/v1/admin/booking-config")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Booking Config", description = "Configure booking window rules and cancellation cutoff")
public class AdminBookingConfigController {

    private final BookingConfigService service;

    @Operation(summary = "Get the current booking configuration")
    @GetMapping
    public ResponseEntity<BookingConfig> get() {
        return ResponseEntity.ok(service.get());
    }

    @Operation(summary = "Replace the entire booking configuration")
    @PutMapping
    public ResponseEntity<BookingConfig> update(@RequestBody BookingConfig body) {
        return ResponseEntity.ok(service.update(body));
    }

    /**
     * Toggle "one-per-day-per-type" restriction.
     */
    @Operation(summary = "Toggle the one-booking-per-day-per-type restriction")
    @PatchMapping("/one-per-day-per-type")
    public ResponseEntity<BookingConfig> toggleOnePerDay(@RequestParam("enabled") boolean enabled) {
        var cfg = service.get();
        cfg.setOnePerDayPerType(enabled);
        return ResponseEntity.ok(service.update(cfg));
    }

    /**
     * Update the cancellation cutoff in hours (>= 0).
     */
    @Operation(summary = "Update the cancellation cutoff in hours (bookings cannot be cancelled within this window)")
    @PatchMapping("/cancel-cutoff-hours")
    public ResponseEntity<BookingConfig> updateCancelCutoff(@RequestParam("value") int hours) {
        if (hours < 0) hours = 0; 
        var cfg = service.get();
        cfg.setCancelCutoffHours(hours);
        return ResponseEntity.ok(service.update(cfg));
    }
}
