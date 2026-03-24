package com.gymsystem.tenant.context;

import com.gymsystem.tenant.Tenant;

/**
 * Utility for enforcing tenant isolation in service methods.
 *
 * Typical usage in a service:
 *   TenantGuard.requireMatch(booking.getTenant());
 */
public final class TenantGuard {

    private TenantGuard() {}

    /**
     * Throws if the entity's tenant does not match the current request's tenant.
     * Use this defensively in update/delete operations to prevent cross-tenant access.
     */
    public static void requireMatch(Tenant entityTenant) {
        Long current = TenantContext.requireTenantId();
        if (!current.equals(entityTenant.getId())) {
            throw new SecurityException("Cross-tenant access denied");
        }
    }

    /**
     * Returns the current tenant ID or throws a clear error.
     * Shorthand for TenantContext.requireTenantId().
     */
    public static Long currentTenantId() {
        return TenantContext.requireTenantId();
    }
}
