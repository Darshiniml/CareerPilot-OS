package com.careerpilot.backend.modules.ai.company.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "company_intelligence_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyIntelligenceCache {

    @Id
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "acquired_content", nullable = false)
    private String acquiredContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_knowledge", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> structuredKnowledge;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "insights", columnDefinition = "jsonb", nullable = false)
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
