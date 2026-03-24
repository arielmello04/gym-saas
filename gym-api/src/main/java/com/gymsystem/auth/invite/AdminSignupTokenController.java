package com.gymsystem.auth.invite;

import com.gymsystem.auth.invite.dto.CreateTokenRequest;
import com.gymsystem.auth.invite.dto.SignupTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin endpoints to manage signup tokens. */
@RestController
@RequestMapping("/api/v1/admin/invite-tokens")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Invite Tokens", description = "Create and manage signup invite tokens")
public class AdminSignupTokenController {

    private final SignupTokenService service;

    @Operation(summary = "Create a new invite token")
    @PostMapping
    public ResponseEntity<SignupTokenResponse> create(@Valid @RequestBody CreateTokenRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @Operation(summary = "List all invite tokens")
    @GetMapping
    public ResponseEntity<List<SignupTokenResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @Operation(summary = "Deactivate (revoke) an invite token")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
