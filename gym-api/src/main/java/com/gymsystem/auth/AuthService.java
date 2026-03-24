package com.gymsystem.auth;

import com.gymsystem.auth.dto.LoginRequest;
import com.gymsystem.auth.dto.SignupRequest;
import com.gymsystem.auth.invite.SignupToken;
import com.gymsystem.auth.invite.SignupTokenService;
import com.gymsystem.security.jwt.JwtService;
import com.gymsystem.tenant.TenantRole;
import com.gymsystem.tenant.TenantUser;
import com.gymsystem.tenant.TenantUserRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SignupTokenService signupTokenService;
    private final TenantUserRepository tenantUserRepository;

    /**
     * Registers a new user.
     * If the invite token is scoped to a tenant, the user is automatically
     * added as a MEMBER of that tenant and the JWT carries the tenant context.
     */
    @Transactional
    public String signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent())
            throw new IllegalArgumentException("Email already registered");

        SignupToken token = signupTokenService.consumeOrThrow(request.getInviteToken());

        Instant now  = Instant.now();
        User    user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(user);

        // Auto-join tenant if invite is tenant-scoped
        if (token.getTenant() != null) {
            tenantUserRepository.save(TenantUser.builder()
                    .tenant(token.getTenant())
                    .user(user)
                    .role(TenantRole.MEMBER)
                    .active(true)
                    .joinedAt(now)
                    .build());
        }

        return buildToken(user);
    }

    /**
     * Authenticates a user and issues a JWT.
     * If a tenant is resolved in the request context, the JWT embeds
     * the user's role within that tenant.
     */
    public String login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        return buildToken(user);
    }

    // ── private ──────────────────────────────────────────────

    private String buildToken(User user) {
        Long   tenantId   = TenantContext.getTenantId();
        String tenantSlug = TenantContext.getTenantSlug();

        if (tenantId != null) {
            String tenantRole = tenantUserRepository
                    .findByTenantIdAndUserIdAndActiveTrue(tenantId, user.getId())
                    .map(tu -> tu.getRole().name())
                    .orElse(null);
            return jwtService.generateToken(
                    user.getEmail(), user.getRole().name(), tenantSlug, tenantRole);
        }
        return jwtService.generateToken(user.getEmail(), user.getRole().name());
    }
}
