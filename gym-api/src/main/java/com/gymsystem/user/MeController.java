package com.gymsystem.user;

import com.gymsystem.payments.SubscriptionRepository;
import com.gymsystem.payments.SubscriptionStatus;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Authenticated user profile and subscription status")
public class MeController {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Operation(summary = "Get the authenticated user's profile and subscription status")
    @GetMapping
    public ResponseEntity<MeResponse> me() {
        // Resolve current principal e-mail
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User u = userRepository.findByEmail(email).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));

        // Assinatura da academia DESTA requisicao. Buscar so por usuario fazia
        // um aluno de duas academias ver aqui a assinatura da outra.
        Long tenantId = TenantGuard.currentTenantId();
        var sub = subscriptionRepository.findByUserIdAndTenantIdAndStatusIn(
                u.getId(), tenantId, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));

        boolean hasSub = sub.isPresent();
        String status = hasSub ? sub.get().getStatus().name() : null;

        return ResponseEntity.ok(new MeResponse(u.getEmail(), u.getRole().name(), hasSub, status));
    }
}
