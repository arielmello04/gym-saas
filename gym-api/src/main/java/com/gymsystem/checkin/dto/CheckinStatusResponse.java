// src/main/java/com/gymsystem/checkin/dto/CheckinStatusResponse.java
package com.gymsystem.checkin.dto;

import com.gymsystem.checkin.CheckinProvider;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/** Lightweight status for the mobile app to poll/update UI. */
@Data
@AllArgsConstructor
public class CheckinStatusResponse {
    private Long id;
    private CheckinProvider provider;
    private String status;
    private String providerRef;
    private Instant completedAt;
}
