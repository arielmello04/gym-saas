package com.gymsystem.documents;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByUserIdAndTenantIdOrderByUploadedAtDesc(Long userId, Long tenantId);

    List<UserDocument> findByTenantIdOrderByUploadedAtDesc(Long tenantId);
}
