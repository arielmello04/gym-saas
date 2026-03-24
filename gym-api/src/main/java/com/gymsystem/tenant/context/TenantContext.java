package com.gymsystem.tenant.context;

/**
 * ThreadLocal holder for the current request's resolved tenant.
 *
 * Set early in the filter chain (TenantResolutionFilter) and cleared
 * at the end of every request to prevent memory leaks in thread pools.
 *
 * Usage:
 *   TenantContext.getTenantId()   — use in services/repositories
 *   TenantContext.requireTenantId() — throws if not set (defensive)
 */
public final class TenantContext {

    private TenantContext() {}

    private static final ThreadLocal<Long>   TENANT_ID   = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_SLUG = new ThreadLocal<>();

    public static void set(Long tenantId, String slug) {
        TENANT_ID.set(tenantId);
        TENANT_SLUG.set(slug);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static String getTenantSlug() {
        return TENANT_SLUG.get();
    }

    /**
     * Returns the tenant ID or throws if the context was not populated.
     * Use this in services where a tenant is always expected.
     */
    public static Long requireTenantId() {
        Long id = TENANT_ID.get();
        if (id == null) {
            throw new IllegalStateException("No tenant in context — request missing X-Tenant-ID header or slug");
        }
        return id;
    }

    /** Must be called at the end of every request (done by TenantResolutionFilter). */
    public static void clear() {
        TENANT_ID.remove();
        TENANT_SLUG.remove();
    }
}
