// src/main/java/com/gymsystem/profile/dto/ProfilePreferencesResponse.java
package com.gymsystem.profile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

@Data
@AllArgsConstructor
public class ProfilePreferencesResponse {
    private boolean allowRecording;
    private boolean allowPhotos;
    private boolean allowFaceVisibility;
    private String notes;
    private Instant updatedAt;
}
