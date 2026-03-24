package com.gymsystem.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Minimal identity + subscription snapshot for the frontend. */
@Data
@AllArgsConstructor
public class MeResponse {
    private String email;
    private String role;
    private boolean hasSubscription;
    private String subscriptionStatus; // null if not present
}
