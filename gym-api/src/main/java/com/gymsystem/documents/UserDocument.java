package com.gymsystem.documents;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_documents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 20)  private String category;
    @Column(name = "mime_type", nullable = false, length = 100) private String mimeType;
    @Column(name = "size_bytes", nullable = false)              private long sizeBytes;
    @Column(name = "storage_path", nullable = false, length = 255) private String storagePath;
    @Column(name = "uploaded_at", nullable = false)             private Instant uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;
}
