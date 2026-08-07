package com.careerpilot.backend.modules.ai.knowledge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "embedding_references")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingReference {

    @Id
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private AiDocument document;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false)
    private AiChunk chunk;
    
    @Column(nullable = false)
    private String provider;
    
    @Column(nullable = false)
    private String collection;
    
    @Column(name = "vector_id", nullable = false)
    private String vectorId;
    
    @Column(name = "embedding_model", nullable = false)
    private String embeddingModel;
    
    @Column(name = "embedding_version", nullable = false)
    private String embeddingVersion;
    
    @Column(name = "vector_dimension", nullable = false)
    private int vectorDimension;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
