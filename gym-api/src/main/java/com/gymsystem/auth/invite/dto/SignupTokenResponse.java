package com.gymsystem.auth.invite.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class SignupTokenResponse {
    private Long id;
    private String code;
    private int maxUses;
    private int usedCount;
    private boolean active;
    private Instant createdAt;
    private Instant expiresAt;
}
