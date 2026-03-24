package com.gymsystem.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Service responsible for creating and validating JWT tokens.
 *
 * Token claims:
 *   sub        — user email
 *   role       — global UserRole (USER / ADMIN_APP / ADMIN_WEB)
 *   tenantSlug — resolved tenant slug at login time (optional)
 *   tenantRole — user's role within the tenant (OWNER/MANAGER/STAFF/TRAINER/MEMBER)
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE        = "role";
    public static final String CLAIM_TENANT_SLUG = "tenantSlug";
    public static final String CLAIM_TENANT_ROLE = "tenantRole";

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-seconds}")
    private long expirationSeconds;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    /** Generates a JWT without tenant context (platform-wide admin). */
    public String generateToken(String subject, String role) {
        return buildToken(subject, role, null, null);
    }

    /** Generates a JWT with tenant context embedded. */
    public String generateToken(String subject, String role, String tenantSlug, String tenantRole) {
        return buildToken(subject, role, tenantSlug, tenantRole);
    }

    /** Validates a JWT and returns its claims; null if invalid. */
    public Claims validateAndParseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildToken(String subject, String role, String tenantSlug, String tenantRole) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        var builder = Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .claim(CLAIM_ROLE, role);

        if (tenantSlug != null) builder.claim(CLAIM_TENANT_SLUG, tenantSlug);
        if (tenantRole != null) builder.claim(CLAIM_TENANT_ROLE, tenantRole);

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }
}
