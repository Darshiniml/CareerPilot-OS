package com.careerpilot.backend.modules.ai.resume.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "resume_validation_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeValidationReport {

    @Id
    private UUID id;
    
    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    
    @Column(name = "has_email", nullable = false)
    @Builder.Default
    private boolean hasEmail = false;
    
    @Column(name = "has_phone", nullable = false)
    @Builder.Default
    private boolean hasPhone = false;
    
    @Column(name = "has_linkedin", nullable = false)
    @Builder.Default
    private boolean hasLinkedin = false;
    
    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "validation_warnings", columnDefinition = "text")
    private Map<String, Object> validationWarnings;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
