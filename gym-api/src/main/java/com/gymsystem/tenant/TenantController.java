package com.gymsystem.tenant;

import com.gymsystem.tenant.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@Tag(name = "Tenant", description = "Tenant management — gym registration and member linking")
public class TenantController {

    private final TenantService tenantService;

    // ── Tenant ────────────────────────────────────────────────

    @Operation(summary = "Register a new gym/tenant (any authenticated user becomes OWNER)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest req) {
        return tenantService.createTenant(req);
    }

    @Operation(summary = "Get current tenant details (resolved from X-Tenant-ID header)")
    @GetMapping
    public TenantResponse getCurrent() {
        return tenantService.getCurrentTenant();
    }

    @Operation(summary = "Update tenant name or plan (OWNER only)")
    @PatchMapping
    @PreAuthorize("hasAuthority('ROLE_OWNER') or hasAuthority('ROLE_ADMIN_WEB')")
    public TenantResponse update(@Valid @RequestBody UpdateTenantRequest req) {
        return tenantService.updateTenant(req);
    }

    // ── Members ───────────────────────────────────────────────

    @Operation(summary = "List all members of the current tenant (OWNER/MANAGER)")
    @GetMapping("/members")
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_MANAGER','ROLE_ADMIN_WEB','ROLE_ADMIN_APP')")
    public List<TenantMemberResponse> listMembers() {
        return tenantService.listMembers();
    }

    @Operation(summary = "Add a registered user to the current tenant with a given role")
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_MANAGER','ROLE_ADMIN_WEB')")
    public TenantMemberResponse addMember(@Valid @RequestBody AddMemberRequest req) {
        return tenantService.addMember(req);
    }

    @Operation(summary = "Get the calling user's membership in the current tenant")
    @GetMapping("/members/me")
    public TenantMemberResponse myMembership() {
        return tenantService.getMyMembership();
    }

    @Operation(summary = "Remove a member from the current tenant (soft-delete)")
    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_OWNER','ROLE_ADMIN_WEB')")
    public void removeMember(@PathVariable Long userId) {
        tenantService.removeMember(userId);
    }
}
