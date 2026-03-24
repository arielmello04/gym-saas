package com.gymsystem.security.jwt;

import com.gymsystem.security.userdetails.CustomUserDetailsService;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.tenant.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT auth filter — extracts and validates the token, then:
 *  1. Sets the Spring Security authentication with BOTH global role and tenant role as authorities.
 *  2. Populates TenantContext from the token's tenantSlug claim (if present), overriding
 *     the header-based resolution only when the token already carries a tenant.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token  = authHeader.substring(7);
        Claims claims = jwtService.validateAndParseClaims(token);

        if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = claims.getSubject();
            var userDetails = userDetailsService.loadUserByUsername(email);

            // Build authorities: global role + tenant role (if present)
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            String globalRole = claims.get(JwtService.CLAIM_ROLE, String.class);
            if (globalRole != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + globalRole));
            }

            String tenantRole = claims.get(JwtService.CLAIM_TENANT_ROLE, String.class);
            if (tenantRole != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + tenantRole));
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Enrich TenantContext from token if not already set by TenantResolutionFilter
            String tenantSlug = claims.get(JwtService.CLAIM_TENANT_SLUG, String.class);
            if (tenantSlug != null && TenantContext.getTenantId() == null) {
                tenantRepository.findBySlugAndActiveTrue(tenantSlug)
                        .ifPresent(t -> TenantContext.set(t.getId(), t.getSlug()));
            }
        }

        filterChain.doFilter(request, response);
    }
}
