package com.gymsystem.booking;

import com.gymsystem.booking.dto.AdminUpdatePolicyRequest;
import com.gymsystem.booking.dto.BookingPolicyResponse;
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
public class AdminBookingPolicyService {

    private final BookingPolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public BookingPolicyResponse getPolicy() {
        Long tenantId = TenantGuard.currentTenantId();
        BookingPolicy policy = policyRepository.findTopByTenantIdOrderByIdAsc(tenantId)
                .orElseGet(() -> createDefaultPolicy(tenantId));
        return toResponse(policy);
    }

    @Transactional
    public BookingPolicyResponse updatePolicy(AdminUpdatePolicyRequest request) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        var  now      = Instant.now();
        var  admin    = getCurrentUser();

        BookingPolicy policy = policyRepository.findTopByTenantIdOrderByIdAsc(tenantId)
                .orElseGet(() -> BookingPolicy.builder()
                        .tenant(tenant)
                        .createdAt(now)
                        .createdByAdminId(admin.getId())
                        .openDaysInAdvance(request.getOpenDaysInAdvance())
                        .updatedAt(now)
                        .build());

        policy.setOpenDaysInAdvance(request.getOpenDaysInAdvance());
        policy.setUpdatedAt(now);
        return toResponse(policyRepository.save(policy));
    }

    private BookingPolicy createDefaultPolicy(Long tenantId) {
        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        var now    = Instant.now();
        return policyRepository.save(BookingPolicy.builder()
                .tenant(tenant)
                .openDaysInAdvance(15)
                .createdByAdminId(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private BookingPolicyResponse toResponse(BookingPolicy p) {
        return new BookingPolicyResponse(p.getOpenDaysInAdvance(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private User getCurrentUser() {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
