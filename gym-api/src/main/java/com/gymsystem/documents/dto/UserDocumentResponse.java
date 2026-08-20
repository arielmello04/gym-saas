package com.gymsystem.documents.dto;

import java.time.Instant;

public record UserDocumentResponse(
        Long id,
        String title,
        String category,
        String mimeType,
        long sizeBytes,
        Instant uploadedAt
) {}
