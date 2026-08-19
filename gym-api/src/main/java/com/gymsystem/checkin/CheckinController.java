// src/main/java/com/gymsystem/checkin/CheckinController.java
package com.gymsystem.checkin;

import com.gymsystem.checkin.dto.CheckinItem;
import com.gymsystem.checkin.dto.StartCheckinRequest;
import com.gymsystem.checkin.dto.StartCheckinResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** User-facing check-in endpoints. */
@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Check-in", description = "User check-in flow and provider callback")
public class CheckinController {

    private final CheckinService service;

    @Operation(summary = "Start a check-in for a booking — triggers provider verification flow")
    @PostMapping("/start")
    public ResponseEntity<StartCheckinResponse> start(@Valid @RequestBody StartCheckinRequest req) {
        return ResponseEntity.ok(service.start(req));
    }

    @Operation(summary = "List my check-in history")
    @GetMapping("/history")
    public ResponseEntity<List<CheckinItem>> history() {
        return ResponseEntity.ok(service.myHistory());
    }
}
