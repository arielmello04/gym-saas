// src/main/java/com/gymsystem/documents/dto/UploadResponse.java
package com.gymsystem.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;

/** Minimal response after uploading a document. */
@Data @AllArgsConstructor
public class UploadResponse {
    private Long id;
    private String title;
    private String category;
    private String mimeType;
    private long sizeBytes;
    private Instant uploadedAt;
}
