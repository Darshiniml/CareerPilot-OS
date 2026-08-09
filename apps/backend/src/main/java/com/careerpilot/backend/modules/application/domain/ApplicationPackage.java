package com.careerpilot.backend.modules.application.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_packages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPackage {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "candidate_profile_json", columnDefinition = "TEXT")
    private String candidateProfileJson;

    @Column(name = "selected_resume_json", columnDefinition = "TEXT")
    private String selectedResumeJson;

    @Column(name = "job_details_json", columnDefinition = "TEXT")
    private String jobDetailsJson;

    @Column(name = "match_result_json", columnDefinition = "TEXT")
    private String matchResultJson;

    @Column(name = "company_intelligence_json", columnDefinition = "TEXT")
    private String companyIntelligenceJson;

    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "submission_capability_json", columnDefinition = "TEXT")
    private String submissionCapabilityJson;

    @Column(name = "preflight_result_json", columnDefinition = "TEXT")
    private String preflightResultJson;

    @Column(name = "official_apply_url", length = 1024)
    private String officialApplyUrl;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
