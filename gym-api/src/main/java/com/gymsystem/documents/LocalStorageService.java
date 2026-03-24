// src/main/java/com/gymsystem/documents/LocalStorageService.java
package com.gymsystem.documents;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Stores files under a single base path, generating unique file names. */
@Component
@RequiredArgsConstructor
public class LocalStorageService {

    @Value("${storage.local.base-path:./storage}")
    private String basePath;

    /** Saves the bytes and returns the relative path suitable for later reads. */
    public String save(byte[] bytes, String originalFilename) throws IOException {
        // Use UUID prefix to avoid collisions while keeping original extension when present.
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            ext = originalFilename.substring(dot);
        }
        String fileName = UUID.randomUUID() + ext;

        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path target = base.resolve(fileName).normalize();

        // Fail-safe: ensure target is still inside base
        if (!target.startsWith(base)) {
            throw new SecurityException("Invalid path");
        }
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return fileName; // store only the relative filename
    }

    /** Resolves a relative path to an absolute on-disk path for reading. */
    public Path resolve(String relativePath) {
        return Paths.get(basePath).toAbsolutePath().normalize().resolve(relativePath).normalize();
    }

    /** Deletes a stored file if present. */
    public void deleteIfExists(String relativePath) throws IOException {
        Files.deleteIfExists(resolve(relativePath));
    }
}
