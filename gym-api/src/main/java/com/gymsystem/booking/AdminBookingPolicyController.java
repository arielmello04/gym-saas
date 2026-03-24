package com.gymsystem.booking;

import com.gymsystem.booking.dto.AdminUpdatePolicyRequest;
import com.gymsystem.booking.dto.BookingPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin endpoints to read and update the global booking policy. */
@RestController
@RequestMapping("/api/v1/admin/booking-policy")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Booking Policy", description = "Read and update the global booking policy")
public class AdminBookingPolicyController {

    private final AdminBookingPolicyService service;

    @Operation(summary = "Get the current booking policy")
    @GetMapping
    public ResponseEntity<BookingPolicyResponse> get() {
        return ResponseEntity.ok(service.getPolicy());
    }

    @Operation(summary = "Update the booking policy")
    @PutMapping
    public ResponseEntity<BookingPolicyResponse> update(@Valid @RequestBody AdminUpdatePolicyRequest request) {
        return ResponseEntity.ok(service.updatePolicy(request));
    }
}
