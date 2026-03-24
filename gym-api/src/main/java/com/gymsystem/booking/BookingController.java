package com.gymsystem.booking;

import com.gymsystem.booking.dto.AvailabilityItem;
import com.gymsystem.booking.dto.BookingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** User-facing endpoints for class availability and bookings. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Browse availability and manage your bookings")
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "List available sessions within a date/time window")
    @GetMapping("/classes/availability")
    public ResponseEntity<List<AvailabilityItem>> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(bookingService.getAvailability(from, to));
    }

    @Operation(summary = "Book a class session for the authenticated user")
    @PostMapping("/classes/{sessionId}/book")
    public ResponseEntity<BookingResponse> book(@PathVariable Long sessionId) {
        return ResponseEntity.ok(bookingService.bookSession(sessionId));
    }

    @Operation(summary = "Cancel a booking that belongs to the authenticated user")
    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> cancel(@PathVariable Long bookingId) {
        bookingService.cancelMyBooking(bookingId);
        return ResponseEntity.noContent().build();
    }
}
