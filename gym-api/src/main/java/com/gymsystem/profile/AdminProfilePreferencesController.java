// src/main/java/com/gymsystem/profile/AdminProfilePreferencesController.java
package com.gymsystem.profile;

import com.gymsystem.profile.dto.ProfilePreferencesResponse;
import com.gymsystem.profile.dto.UpdateProfilePreferencesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/preferences")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Profile Preferences", description = "Read and update profile preferences for any user")
public class AdminProfilePreferencesController {

    private final ProfilePreferencesService service;

    @Operation(summary = "Get profile preferences for a specific user")
    @GetMapping
    public ResponseEntity<ProfilePreferencesResponse> getForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getForUser(userId));
    }

    @Operation(summary = "Update profile preferences for a specific user")
    @PutMapping
    public ResponseEntity<ProfilePreferencesResponse> updateForUser(@PathVariable Long userId,
                                                                    @Valid @RequestBody UpdateProfilePreferencesRequest req) {
        return ResponseEntity.ok(service.updateForUser(userId, req));
    }
}
