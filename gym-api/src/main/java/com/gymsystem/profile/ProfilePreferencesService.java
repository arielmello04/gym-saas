package com.gymsystem.profile;

import com.gymsystem.profile.dto.ProfilePreferencesResponse;
import com.gymsystem.profile.dto.UpdateProfilePreferencesRequest;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProfilePreferencesService {

    private final ProfilePreferencesRepository repository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public ProfilePreferencesResponse getMy() {
        return toResponse(getOrCreate(currentUser()));
    }

    @Transactional
    public ProfilePreferencesResponse updateMy(UpdateProfilePreferencesRequest req) {
        var pref = getOrCreate(currentUser());
        applyUpdate(pref, req);
        return toResponse(repository.save(pref));
    }

    public ProfilePreferencesResponse getForUser(Long userId) {
        Long tenantId = TenantGuard.currentTenantId();
        var pref = repository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Preferences not found for user"));
        return toResponse(pref);
    }

    @Transactional
    public ProfilePreferencesResponse updateForUser(Long userId, UpdateProfilePreferencesRequest req) {
        Long tenantId = TenantGuard.currentTenantId();
        var pref = repository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Preferences not found for user"));
        applyUpdate(pref, req);
        return toResponse(repository.save(pref));
    }

    // ── helpers ──────────────────────────────────────────────

    private ProfilePreferences getOrCreate(User user) {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantId(user.getId(), tenantId)
                .orElseGet(() -> {
                    var tenant = tenantRepository.findById(tenantId).orElseThrow();
                    return repository.save(ProfilePreferences.builder()
                            .user(user).tenant(tenant)
                            .allowRecording(true).allowPhotos(true).allowFaceVisibility(true)
                            .updatedAt(Instant.now()).build());
                });
    }

    private void applyUpdate(ProfilePreferences pref, UpdateProfilePreferencesRequest req) {
        if (req.getAllowRecording()     != null) pref.setAllowRecording(req.getAllowRecording());
        if (req.getAllowPhotos()        != null) pref.setAllowPhotos(req.getAllowPhotos());
        if (req.getAllowFaceVisibility() != null) pref.setAllowFaceVisibility(req.getAllowFaceVisibility());
        if (req.getNotes()             != null) pref.setNotes(req.getNotes());
        pref.setUpdatedAt(Instant.now());
    }

    private ProfilePreferencesResponse toResponse(ProfilePreferences p) {
        return new ProfilePreferencesResponse(
                p.isAllowRecording(), p.isAllowPhotos(), p.isAllowFaceVisibility(),
                p.getNotes(), p.getUpdatedAt());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
