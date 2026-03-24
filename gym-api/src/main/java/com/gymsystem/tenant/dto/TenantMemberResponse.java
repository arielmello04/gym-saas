package com.gymsystem.tenant.dto;

import java.time.Instant;

public record TenantMemberResponse(
    Long membershipId,
    Long userId,
    String email,
    String role,
    boolean active,
    Instant joinedAt
) {}
