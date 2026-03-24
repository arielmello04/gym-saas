package com.gymsystem.tenant;

/**
 * Roles a user can hold within a specific tenant.
 * A single user can be OWNER of one gym and MEMBER of another.
 */
public enum TenantRole {
    /** Full control: manage staff, billing, settings. */
    OWNER,
    /** Day-to-day operations: manage members, sessions, bookings. */
    MANAGER,
    /** Front-desk: check members in, view schedule. */
    STAFF,
    /** Teaches classes; can view their own sessions. */
    TRAINER,
    /** Regular gym member: book classes, view own data. */
    MEMBER
}
