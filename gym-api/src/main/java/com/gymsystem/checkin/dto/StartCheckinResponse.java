// src/main/java/com/gymsystem/checkin/dto/StartCheckinResponse.java
package com.gymsystem.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Response indicates if client should redirect to provider app/URL. */
@Data @AllArgsConstructor
public class StartCheckinResponse {
    private String redirectUrl; // null for DIRECT (auto-completed)
    private Long checkinId;     // internal id for tracking
}
