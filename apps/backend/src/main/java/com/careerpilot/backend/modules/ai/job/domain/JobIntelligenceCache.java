package com.careerpilot.backend.modules.ai.job.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "job_intelligence_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobIntelligenceCache {

    @Id
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "parsed_text", nullable = false)
    private String parsedText;

    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "structured_knowledge", columnDefinition = "text", nullable = false)
    private Map<String, Object> structuredKnowledge;

    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "metadata", columnDefinition = "text", nullable = false)
    private Map<String, Object> metadata;

    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "quality_metrics", columnDefinition = "text", nullable = false)
    private Map<String, Object> qualityMetrics;

    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "insights", columnDefinition = "text", nullable = false)
    private Map<String, Object> insights;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "source_version", length = 20)
    private String sourceVersion;

    @Column(name = "crawl_timestamp")
    private Instant crawlTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
