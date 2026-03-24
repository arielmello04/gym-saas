package com.gymsystem.tenant.dto;

import java.time.Instant;

public record TenantResponse(
    Long id,
    String slug,
    String name,
    String plan,
    boolean active,
    Instant createdAt
) {}
