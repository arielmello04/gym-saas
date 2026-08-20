package com.gymsystem.tenant;

import com.gymsystem.booking.config.BookingConfig;
import com.gymsystem.booking.config.BookingConfigRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.tenant.dto.*;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;
    private final BookingConfigRepository bookingConfigRepository;

    // ─── Tenant CRUD ─────────────────────────────────────────

    /** Creates a new tenant and makes the caller its OWNER. */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest req) {
        if (tenantRepository.existsBySlug(req.getSlug())) {
            throw new IllegalArgumentException("Slug already taken: " + req.getSlug());
        }

        Instant now = Instant.now();
        Tenant tenant = Tenant.builder()
                .slug(req.getSlug().toLowerCase())
                .name(req.getName())
                .active(true)
                .plan(TenantPlan.BASIC)
                .createdAt(now)
                .updatedAt(now)
                .build();
        tenant = tenantRepository.save(tenant);

        // Auto-link the creator as OWNER
        User caller = getCurrentUser();
        TenantUser ownership = TenantUser.builder()
                .tenant(tenant)
                .user(caller)
                .role(TenantRole.OWNER)
                .active(true)
                .joinedAt(now)
                .build();
        tenantUserRepository.save(ownership);

        bookingConfigRepository.save(defaultBookingConfig(tenant, now));

        return toResponse(tenant);
    }

    /**
     * Configuracao inicial de reservas da academia.
     *
     * Sem esta linha, /my/bookings, /classes/calendar e /admin/booking-config
     * respondem erro para a academia recem-criada: o BookingConfigService espera
     * uma configuracao por tenant e nao inventa uma. Os valores repetem os
     * padroes que o schema ja usava.
     */
    private BookingConfig defaultBookingConfig(Tenant tenant, Instant now) {
        return BookingConfig.builder()
                .tenant(tenant)
                .publishDaysBeforeMonth(15)
                .businessDays("MON-SAT")
                .businessStart(LocalTime.of(8, 0))
                .businessEnd(LocalTime.of(18, 0))
                .cancelCutoffHours(0)
                .onePerDayPerType(true)
                .waitlistEnabled(true)
                .waitlistPromotionHours(2)
                .updatedAt(now)
                .build();
    }

    /** Returns the currently resolved tenant's details. */
    public TenantResponse getCurrentTenant() {
        Long tenantId = TenantContext.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
        return toResponse(tenant);
    }

    /** Updates name or plan of the current tenant (OWNER only — enforced by @PreAuthorize). */
    @Transactional
    public TenantResponse updateTenant(UpdateTenantRequest req) {
        Long tenantId = TenantContext.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        if (req.getName() != null)  tenant.setName(req.getName());
        if (req.getPlan() != null)  tenant.setPlan(req.getPlan());
        tenant.setUpdatedAt(Instant.now());

        return toResponse(tenantRepository.save(tenant));
    }

    // ─── Member management ───────────────────────────────────

    /** Adds a user to the current tenant with the given role. */
    @Transactional
    public TenantMemberResponse addMember(AddMemberRequest req) {
        Long tenantId = TenantContext.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.getEmail()));

        if (tenantUserRepository.existsByTenantIdAndUserId(tenantId, user.getId())) {
            throw new IllegalArgumentException("User already belongs to this tenant");
        }

        TenantUser tu = TenantUser.builder()
                .tenant(tenant)
                .user(user)
                .role(req.getRole())
                .active(true)
                .joinedAt(Instant.now())
                .build();
        tu = tenantUserRepository.save(tu);
        return toMemberResponse(tu);
    }

    /** Lists all active members of the current tenant. */
    public List<TenantMemberResponse> listMembers() {
        Long tenantId = TenantContext.requireTenantId();
        return tenantUserRepository.findByTenantIdAndActiveTrue(tenantId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    /** Returns the calling user's role in the current tenant. */
    public TenantMemberResponse getMyMembership() {
        Long tenantId = TenantContext.requireTenantId();
        User user = getCurrentUser();
        TenantUser tu = tenantUserRepository
                .findByTenantIdAndUserIdAndActiveTrue(tenantId, user.getId())
                .orElseThrow(() -> new IllegalStateException("You are not a member of this tenant"));
        return toMemberResponse(tu);
    }

    /** Deactivates a user's membership in the current tenant. */
    @Transactional
    public void removeMember(Long userId) {
        Long tenantId = TenantContext.requireTenantId();
        TenantUser tu = tenantUserRepository
                .findByTenantIdAndUserIdAndActiveTrue(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found in this tenant"));
        tu.setActive(false);
        tenantUserRepository.save(tu);
    }

    // ─── Helpers ─────────────────────────────────────────────

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(t.getId(), t.getSlug(), t.getName(),
                t.getPlan().name(), t.isActive(), t.getCreatedAt());
    }

    private TenantMemberResponse toMemberResponse(TenantUser tu) {
        return new TenantMemberResponse(
                tu.getId(),
                tu.getUser().getId(),
                tu.getUser().getEmail(),
                tu.getRole().name(),
                tu.isActive(),
                tu.getJoinedAt()
        );
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
