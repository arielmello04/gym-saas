package com.gymsystem.documents;

import com.gymsystem.documents.dto.UploadResponse;
import com.gymsystem.documents.dto.UserDocumentResponse;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.context.TenantGuard;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_MIME = Set.of(
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE);

    private final UserDocumentRepository repository;
    private final LocalStorageService storage;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public List<UserDocumentResponse> myDocuments() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantIdOrderByUploadedAtDesc(currentUser().getId(), tenantId)
                .stream().map(this::toResponse).toList();
    }

    public List<UserDocumentResponse> listByUser(Long userId) {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantIdOrderByUploadedAtDesc(userId, tenantId)
                .stream().map(this::toResponse).toList();
    }

    public List<UserDocumentResponse> listAll() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByTenantIdOrderByUploadedAtDesc(tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public UploadResponse uploadMy(String title, String category, MultipartFile file) {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        User me       = currentUser();

        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        String mime = detectMime(file);
        if (!ALLOWED_MIME.contains(mime))   throw new IllegalArgumentException("Unsupported file type");

        String relativePath;
        try {
            relativePath = storage.save(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store document", e);
        }
        var now = Instant.now();

        UserDocument doc = UserDocument.builder()
                .user(me).tenant(tenant)
                .title(title).category(category)
                .mimeType(mime).sizeBytes(file.getSize())
                .storagePath(relativePath)
                .uploadedAt(now).uploadedBy(me)
                .build();

        var saved = repository.save(doc);
        return new UploadResponse(saved.getId(), saved.getTitle(), saved.getCategory(),
                saved.getMimeType(), saved.getSizeBytes(), saved.getUploadedAt());
    }

    @Transactional
    public void deleteMy(Long documentId) {
        Long tenantId = TenantGuard.currentTenantId();
        User me  = currentUser();
        var  doc = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (!doc.getTenant().getId().equals(tenantId))
            throw new SecurityException("Document does not belong to this tenant");
        if (!doc.getUser().getId().equals(me.getId()))
            throw new SecurityException("Not allowed to delete this document");

        try {
            storage.deleteIfExists(doc.getStoragePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete document from storage", e);
        }
        repository.delete(doc);
    }

    private User currentUser() {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String detectMime(MultipartFile file) {
        String headerType = file.getContentType();
        if (headerType != null && ALLOWED_MIME.contains(headerType)) return headerType;
        try {
            String probed = Files.probeContentType(file.getResource().getFile().toPath());
            return probed != null ? probed : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to detect uploaded file type", e);
        }
    }

    private UserDocumentResponse toResponse(UserDocument doc) {
        return new UserDocumentResponse(
                doc.getId(), doc.getTitle(), doc.getCategory(),
                doc.getMimeType(), doc.getSizeBytes(), doc.getUploadedAt());
    }
}
