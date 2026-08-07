package com.careerpilot.backend.modules.ai.knowledge.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_document_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentVersion {

    @Id
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_document_id", nullable = false)
    private AiDocument parentDocument;
    
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "generated_by_ai", nullable = false)
    @Builder.Default
    private boolean generatedByAi = false;
    
    @Column(name = "change_reason")
    private String changeReason;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
