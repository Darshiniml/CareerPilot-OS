package com.careerpilot.backend.modules.application.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_decisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDecision {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String recommendation;

    @Column(name = "decision_rationale", columnDefinition = "TEXT")
    private String decisionRationale;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "critical_gaps_json", columnDefinition = "TEXT")
    private String criticalGapsJson;

    @Column(name = "recommended_resume_id")
    private UUID recommendedResumeId;

    @Column(name = "recommended_resume_title")
    private String recommendedResumeTitle;

    @Column(name = "company_highlights_json", columnDefinition = "TEXT")
    private String companyHighlightsJson;

    @Column(name = "preflight_result_json", columnDefinition = "TEXT")
    private String preflightResultJson;

    @Column(name = "submission_capability_json", columnDefinition = "TEXT")
    private String submissionCapabilityJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
