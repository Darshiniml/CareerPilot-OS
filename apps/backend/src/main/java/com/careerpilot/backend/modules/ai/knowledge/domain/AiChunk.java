package com.careerpilot.backend.modules.ai.knowledge.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_document_chunks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChunk {

    @Id
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private AiDocument document;
    
    @Column(name = "chunk_number", nullable = false)
    private int chunkNumber;
    
    @Column(nullable = false)
    private String text;
    
    @Column(name = "token_count", nullable = false)
    private int tokenCount;
    
    @Column(name = "page_number")
    private Integer pageNumber;
    
    private String section;
    private String heading;
    
    @Column(name = "chunk_type")
    private String chunkType;
    
    @Column(name = "source_document_version")
    private Integer sourceDocumentVersion;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
