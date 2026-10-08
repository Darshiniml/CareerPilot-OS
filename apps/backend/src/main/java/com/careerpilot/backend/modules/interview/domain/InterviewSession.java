package com.careerpilot.backend.modules.interview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "interview_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    private UUID sessionId;

    /** Optional: the candidate's application this practice is for. */
    @Column(name = "application_id")
    private UUID applicationId;

    /** Optional: the discovered job this practice is for. */
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type")
    private InterviewType interviewType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "difficulty", length = 10)
    private String difficulty;

    @Column(name = "target_role")
    private String targetRole;

    @Column(name = "target_company")
    private String targetCompany;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Mean overall score of answered questions (0..1), computed by CareerPilot. 0 until answered. */
    @Column(name = "overall_readiness")
    private double overallReadiness;

    /** AI session feedback (summary, strong/weak areas, next steps) produced on completion. */
    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    // Eager: a session has at most 10 questions and is always returned with them.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id")
    @OrderBy("questionOrder ASC")
    @Builder.Default
    private List<InterviewQuestion> questions = new ArrayList<>();
}
