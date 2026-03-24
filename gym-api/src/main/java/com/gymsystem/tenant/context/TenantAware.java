package com.gymsystem.tenant.context;

import com.gymsystem.tenant.Tenant;

/**
 * Marker interface for JPA entities that belong to a tenant.
 * Implement this on every entity that has a tenant_id column.
 *
 * Usage:
 *   @ManyToOne(fetch = FetchType.LAZY)
 *   @JoinColumn(name = "tenant_id", nullable = false)
 *   private Tenant tenant;
 *
 *   // then implement getTenant() from this interface
 */
public interface TenantAware {
    Tenant getTenant();
    void setTenant(Tenant tenant);
}
