package com.gymsystem.booking;

import com.gymsystem.booking.dto.GenerateMonthRequest;
import com.gymsystem.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Admin helper to generate monthly class sessions automatically. */
@RestController
@RequestMapping("/api/v1/admin/classes/generator")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Schedule Generator", description = "Bulk-generate class sessions for a given month")
public class AdminScheduleGeneratorController {

    private final MonthlyScheduleGenerator generator;
    private final UserRepository userRepository;

    @Operation(summary = "Generate all class sessions for a given month based on the recurring schedule")
    @PostMapping("/month")
    public ResponseEntity<String> generate(@Valid @RequestBody GenerateMonthRequest req) {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        var admin = userRepository.findByEmail(email).orElseThrow();
        int created = generator.generate(req, admin.getId());
        return ResponseEntity.ok("Created sessions: " + created);
    }
}
