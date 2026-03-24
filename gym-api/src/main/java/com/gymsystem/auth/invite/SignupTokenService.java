package com.gymsystem.auth.invite;

import com.gymsystem.auth.invite.dto.CreateTokenRequest;
import com.gymsystem.auth.invite.dto.SignupTokenResponse;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignupTokenService {

    private final SignupTokenRepository repository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    /** Admin creates a new invite token scoped to the current tenant. */
    @Transactional
    public SignupTokenResponse create(CreateTokenRequest req) {
        User   admin    = currentUser();
        Long   tenantId = TenantContext.getTenantId(); // nullable — global admin may have no tenant
        var    tenant   = (tenantId != null) ? tenantRepository.findById(tenantId).orElse(null) : null;

        int     maxUses   = req.getMaxUses() == null ? 1 : req.getMaxUses();
        Instant expiresAt = req.getExpiresInDays() == null
                ? Instant.now().plus(15, ChronoUnit.DAYS)
                : Instant.now().plus(req.getExpiresInDays(), ChronoUnit.DAYS);

        String code = RandomStringUtils.randomAlphanumeric(10).toUpperCase();

        SignupToken token = SignupToken.builder()
                .createdBy(admin)
                .tenant(tenant)
                .code(code)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .maxUses(maxUses)
                .usedCount(0)
                .active(true)
                .build();

        return toResponse(repository.save(token));
    }

    /** Lists tokens for the current tenant (or all if no tenant context). */
    public List<SignupTokenResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                    .stream().map(this::toResponse).toList();
        }
        return repository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toResponse).toList();
    }

    @Transactional
    public void deactivate(Long id) {
        SignupToken token = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        token.setActive(false);
        repository.save(token);
    }

    /** Validates and consumes the token. If token is tenant-scoped, auto-joins user to that tenant. */
    @Transactional
    public SignupToken consumeOrThrow(String code) {
        SignupToken t = repository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive token"));
        if (t.getExpiresAt() != null && Instant.now().isAfter(t.getExpiresAt()))
            throw new IllegalArgumentException("Token has expired");
        if (t.getUsedCount() >= t.getMaxUses())
            throw new IllegalArgumentException("Token usage limit reached");
        t.setUsedCount(t.getUsedCount() + 1);
        repository.save(t);
        return t; // caller uses token.getTenant() to auto-join
    }

    private SignupTokenResponse toResponse(SignupToken t) {
        return new SignupTokenResponse(t.getId(), t.getCode(), t.getMaxUses(), t.getUsedCount(),
                t.isActive(), t.getCreatedAt(), t.getExpiresAt());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
