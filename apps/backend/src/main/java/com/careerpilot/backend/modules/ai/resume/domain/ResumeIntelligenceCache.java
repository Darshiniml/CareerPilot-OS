package com.careerpilot.backend.modules.ai.resume.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "resume_intelligence_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeIntelligenceCache {

    @Id
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;
    
    @Column(name = "parsed_text", nullable = false)
    private String parsedText;
    
    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "structured_knowledge", columnDefinition = "text", nullable = false)
    private Map<String, Object> structuredKnowledge;
    
    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "quality_metrics", columnDefinition = "text", nullable = false)
    private Map<String, Object> qualityMetrics;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
