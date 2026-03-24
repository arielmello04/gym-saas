package com.gymsystem.tenant.context;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Resolves the current tenant early in the filter chain and stores it in TenantContext.
 *
 * Resolution order:
 *  1. Header:    X-Tenant-ID: academia-fit
 *  2. Subdomain: academia-fit.gymsystem.com  → slug = "academia-fit"
 *
 * Public endpoints (auth, swagger, health) proceed without a tenant context.
 * All other endpoints expect a resolved tenant.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String slug = resolveSlug(request);

            if (slug != null && !slug.isBlank()) {
                Optional<Tenant> tenant = tenantRepository.findBySlugAndActiveTrue(slug);
                if (tenant.isPresent()) {
                    TenantContext.set(tenant.get().getId(), tenant.get().getSlug());
                    log.debug("Tenant resolved: {} (id={})", slug, tenant.get().getId());
                } else {
                    log.warn("Unknown or inactive tenant slug: {}", slug);
                    // Context stays null — SecurityConfig will reject protected endpoints
                }
            }

            chain.doFilter(request, response);

        } finally {
            // Always clear to avoid tenant leaking across requests on reused threads
            TenantContext.clear();
        }
    }

    private String resolveSlug(HttpServletRequest request) {
        // 1. Explicit header (preferred for mobile apps and API clients)
        String header = request.getHeader(TENANT_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim().toLowerCase();
        }

        // 2. Subdomain: "academia-fit.gymsystem.com" → "academia-fit"
        String host = request.getServerName(); // e.g. academia-fit.gymsystem.com
        if (host != null && host.contains(".")) {
            String subdomain = host.split("\\.")[0];
            // Ignore "www", "api", "app" as they are not tenant slugs
            if (!subdomain.equalsIgnoreCase("www")
                    && !subdomain.equalsIgnoreCase("api")
                    && !subdomain.equalsIgnoreCase("app")
                    && !subdomain.equalsIgnoreCase("localhost")) {
                return subdomain.toLowerCase();
            }
        }

        return null;
    }
}
