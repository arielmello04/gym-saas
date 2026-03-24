package com.gymsystem.tenant;

/** SaaS subscription plan for the tenant (the gym itself). */
public enum TenantPlan {
    /** Free tier — limited members and features. */
    BASIC,
    /** Full-featured plan. */
    PRO,
    /** Unlimited members, white-label, priority support. */
    ENTERPRISE
}
