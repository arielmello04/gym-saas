// src/main/java/com/gymsystem/booking/MyBookingsController.java
package com.gymsystem.booking;

import com.gymsystem.booking.dto.MyBookingItem;
import com.gymsystem.booking.config.BookingConfigService;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Endpoints to list the current user's bookings (upcoming/past/all).
 * This avoids leaking internal entities and provides a clean shape for the UI.
 */
@RestController
@RequestMapping("/api/v1/my/bookings")
@RequiredArgsConstructor
@Tag(name = "My Bookings", description = "List the authenticated user's bookings")
public class MyBookingsController {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingConfigService bookingConfigService;

    /**
     * Lists bookings for the authenticated user.
     * @param scope upcoming | past | all (defaults to "upcoming")
     */
    @Operation(summary = "List my bookings — scope: upcoming (default) | past | all")
    @GetMapping
    public ResponseEntity<List<MyBookingItem>> list(@RequestParam(name = "scope", defaultValue = "upcoming") String scope) {
        User me = currentUser();
        var rows = bookingRepository.findAllByUserIdWithSession(me.getId());
        var now = Instant.now();
        int cutoff = Math.max(0, bookingConfigService.get().getCancelCutoffHours());

        // Map to DTO and apply the requested scope in-memory.
        var items = rows.stream().map(b -> {
            var s = b.getSession();
            Instant latestAllowed = s.getStartAt().minusSeconds(cutoff * 3600L);
            boolean cancellable = now.isBefore(latestAllowed);
            return new MyBookingItem(
                    b.getId(),
                    s.getId(),
                    s.getClassType().getCode(),
                    s.getClassType().getName(),
                    s.getStartAt(),
                    s.getEndAt(),
                    b.getStatus().name(),
                    cancellable
            );
        }).filter(i -> switch (scope.toLowerCase()) {
            case "upcoming" -> i.getStartAt().isAfter(now);
            case "past"     -> !i.getStartAt().isAfter(now);
            case "all"      -> true;
            default         -> throw new IllegalArgumentException("Invalid scope. Use upcoming | past | all");
        }).toList();

        return ResponseEntity.ok(items);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
