package com.gymsystem.documents;

import com.gymsystem.documents.dto.UploadResponse;
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

    public List<UserDocument> myDocuments() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantIdOrderByUploadedAtDesc(currentUser().getId(), tenantId);
    }

    public List<UserDocument> listByUser(Long userId) {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByUserIdAndTenantIdOrderByUploadedAtDesc(userId, tenantId);
    }

    public List<UserDocument> listAll() {
        Long tenantId = TenantGuard.currentTenantId();
        return repository.findByTenantIdOrderByUploadedAtDesc(tenantId);
    }

    @Transactional
    public UploadResponse uploadMy(String title, String category, MultipartFile file) throws Exception {
        Long tenantId = TenantGuard.currentTenantId();
        var  tenant   = tenantRepository.findById(tenantId).orElseThrow();
        User me       = currentUser();

        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        String mime = detectMime(file);
        if (!ALLOWED_MIME.contains(mime))   throw new IllegalArgumentException("Unsupported file type");

        String relativePath = storage.save(file.getBytes(), file.getOriginalFilename());
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
    public void deleteMy(Long documentId) throws Exception {
        Long tenantId = TenantGuard.currentTenantId();
        User me  = currentUser();
        var  doc = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        if (!doc.getTenant().getId().equals(tenantId))
            throw new SecurityException("Document does not belong to this tenant");
        if (!doc.getUser().getId().equals(me.getId()))
            throw new SecurityException("Not allowed to delete this document");

        storage.deleteIfExists(doc.getStoragePath());
        repository.delete(doc);
    }

    private User currentUser() {
        var email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String detectMime(MultipartFile file) throws Exception {
        String headerType = file.getContentType();
        if (headerType != null && ALLOWED_MIME.contains(headerType)) return headerType;
        String probed = Files.probeContentType(file.getResource().getFile().toPath());
        return probed != null ? probed : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
