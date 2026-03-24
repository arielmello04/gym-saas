package com.gymsystem.user;

import com.gymsystem.payments.SubscriptionRepository;
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

        // Check for active or past-due subscription
        var sub = subscriptionRepository.findByUserIdAndStatusIn(u.getId(), Set.of(
                com.gymsystem.payments.SubscriptionStatus.ACTIVE,
                com.gymsystem.payments.SubscriptionStatus.PAST_DUE
        ));

        boolean hasSub = sub.isPresent();
        String status = hasSub ? sub.get().getStatus().name() : null;

        return ResponseEntity.ok(new MeResponse(u.getEmail(), u.getRole().name(), hasSub, status));
    }
}
