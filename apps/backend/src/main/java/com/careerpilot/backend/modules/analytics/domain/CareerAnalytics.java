package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_analytics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerAnalytics {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "analysis_period", nullable = false)
    private String analysisPeriod; // DAILY, WEEKLY, MONTHLY, QUARTERLY

    @Column(name = "total_jobs_discovered", nullable = false)
    private int totalJobsDiscovered;

    @Column(name = "jobs_matched", nullable = false)
    private int jobsMatched;

    @Column(name = "applications_submitted", nullable = false)
    private int applicationsSubmitted;

    @Column(name = "applications_successful", nullable = false)
    private int applicationsSuccessful;

    @Column(name = "interviews_received", nullable = false)
    private int interviewsReceived;

    @Column(name = "offers_received", nullable = false)
    private int offersReceived;

    @Column(name = "average_match_score", nullable = false)
    private double averageMatchScore;

    @Column(name = "average_interview_readiness", nullable = false)
    private double averageInterviewReadiness;

    @Column(name = "resume_score", nullable = false)
    private double resumeScore;

    @Column(name = "skill_coverage", nullable = false)
    private double skillCoverage;

    @Column(name = "application_success_rate", nullable = false)
    private double applicationSuccessRate;

    @Column(name = "interview_conversion_rate", nullable = false)
    private double interviewConversionRate;

    @Column(name = "offer_conversion_rate", nullable = false)
    private double offerConversionRate;

    @Column(name = "top_skills")
    private String topSkills; // Comma-separated

    @Column(name = "missing_skills")
    private String missingSkills; // Comma-separated

    @Column(name = "trending_skills")
    private String trendingSkills; // Comma-separated

    @Column(name = "career_growth_score", nullable = false)
    private double careerGrowthScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
