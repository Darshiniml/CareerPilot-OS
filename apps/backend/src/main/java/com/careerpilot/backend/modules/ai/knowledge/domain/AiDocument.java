package com.careerpilot.backend.modules.ai.knowledge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocument {

    @Id
    private UUID id;
    
    @Column(name = "document_type", nullable = false)
    private String documentType;
    
    @Column(nullable = false)
    @Builder.Default
    private String status = "CREATED"; // CREATED, VALIDATED, PARSED, METADATA_EXTRACTED, CHUNKED, EMBEDDED, INDEXED, READY, FAILED, ARCHIVED
    
    @Column(name = "owner_id")
    private UUID ownerId;
    
    @Column(nullable = false)
    private String title;
    
    private String source;
    
    @Column(name = "mime_type")
    private String mimeType;
    
    @Builder.Default
    private String language = "en";
    
    private String content;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_metadata", columnDefinition = "jsonb")
    private Map<String, Object> structuredMetadata;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flexible_metadata", columnDefinition = "jsonb")
    private Map<String, Object> flexibleMetadata;
    
    @Builder.Default
    private int version = 1;
    
    private String checksum;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AiChunk> chunks = new ArrayList<>();
}
