// src/main/java/com/gymsystem/documents/AdminDocumentController.java
package com.gymsystem.documents;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.gymsystem.documents.dto.UserDocumentResponse;
import java.nio.file.Path;
import java.util.List;

/** Admin endpoints to inspect/download a user's documents. */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_APP','ADMIN_WEB')")
@Tag(name = "Admin — Documents", description = "Inspect and download documents belonging to any user")
public class AdminDocumentController {

    private final DocumentService service;
    private final LocalStorageService storage;
    private final UserDocumentRepository repository;

    @Operation(summary = "List all documents for a user")
    @GetMapping
    public ResponseEntity<List<UserDocumentResponse>> list(@PathVariable Long userId) {
        return ResponseEntity.ok(service.listByUser(userId));
    }

    @Operation(summary = "Download a specific document for a user")
    @GetMapping("/{docId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long userId, @PathVariable Long docId) {
        var doc = repository.findById(docId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!doc.getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Path path = storage.resolve(doc.getStoragePath());
        var resource = new FileSystemResource(path.toFile());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getTitle() + "\"")
                .body(resource);
    }
}
