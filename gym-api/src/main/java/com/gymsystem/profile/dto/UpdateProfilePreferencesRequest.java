// src/main/java/com/gymsystem/profile/dto/UpdateProfilePreferencesRequest.java
package com.gymsystem.profile.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfilePreferencesRequest {
    private Boolean allowRecording;       // null = keep current
    private Boolean allowPhotos;
    private Boolean allowFaceVisibility;

    @Size(max = 500)
    private String notes;
}
