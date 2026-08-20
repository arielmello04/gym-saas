// src/main/java/com/gymsystem/booking/config/AdminBookingConfigController.java
package com.gymsystem.booking.config;

import com.gymsystem.booking.config.dto.BookingConfigResponse;
import com.gymsystem.booking.config.dto.UpdateBookingConfigRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    public ResponseEntity<BookingConfigResponse> get() {
        return ResponseEntity.ok(BookingConfigResponse.from(service.get()));
    }

    @Operation(summary = "Replace the entire booking configuration")
    @PutMapping
    public ResponseEntity<BookingConfigResponse> update(@Valid @RequestBody UpdateBookingConfigRequest body) {
        return ResponseEntity.ok(BookingConfigResponse.from(service.update(body)));
    }

    @Operation(summary = "Toggle the one-booking-per-day-per-type restriction")
    @PatchMapping("/one-per-day-per-type")
    public ResponseEntity<BookingConfigResponse> toggleOnePerDay(@RequestParam("enabled") boolean enabled) {
        return ResponseEntity.ok(BookingConfigResponse.from(service.setOnePerDayPerType(enabled)));
    }

    @Operation(summary = "Update the cancellation cutoff in hours (bookings cannot be cancelled within this window)")
    @PatchMapping("/cancel-cutoff-hours")
    public ResponseEntity<BookingConfigResponse> updateCancelCutoff(@RequestParam("value") int hours) {
        return ResponseEntity.ok(BookingConfigResponse.from(service.setCancelCutoffHours(hours)));
    }
}
