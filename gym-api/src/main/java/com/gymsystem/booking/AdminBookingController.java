package com.gymsystem.booking;

import com.gymsystem.booking.dto.AdminCreateSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin endpoints for managing class sessions (create and cancel). */
@RestController
@RequestMapping("/api/v1/admin/classes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Class Sessions", description = "Create and cancel class sessions")
public class AdminBookingController {

    private final BookingService bookingService;

    @Operation(summary = "Create a new class session")
    @PostMapping("/sessions")
    public ResponseEntity<Long> createSession(@Valid @RequestBody AdminCreateSessionRequest request) {
        Long id = bookingService.createSession(request);
        return ResponseEntity.ok(id);
    }

    @Operation(summary = "Cancel a session (only if it has no active bookings)")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> cancelSession(@PathVariable Long sessionId) {
        bookingService.cancelSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
