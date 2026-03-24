// src/main/java/com/gymsystem/profile/ProfilePreferencesController.java
package com.gymsystem.profile;

import com.gymsystem.profile.dto.ProfilePreferencesResponse;
import com.gymsystem.profile.dto.UpdateProfilePreferencesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile/preferences")
@RequiredArgsConstructor
@Tag(name = "Profile Preferences", description = "Read and update your own profile preferences")
public class ProfilePreferencesController {

    private final ProfilePreferencesService service;

    @Operation(summary = "Get my profile preferences")
    @GetMapping
    public ResponseEntity<ProfilePreferencesResponse> getMy() {
        return ResponseEntity.ok(service.getMy());
    }

    @Operation(summary = "Update my profile preferences")
    @PutMapping
    public ResponseEntity<ProfilePreferencesResponse> updateMy(@Valid @RequestBody UpdateProfilePreferencesRequest req) {
        return ResponseEntity.ok(service.updateMy(req));
    }
}
