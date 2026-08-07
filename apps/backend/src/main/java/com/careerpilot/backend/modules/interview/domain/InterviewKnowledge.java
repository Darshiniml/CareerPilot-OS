package com.careerpilot.backend.modules.interview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "interview_knowledge")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewKnowledge {
    @Id
    private UUID interviewId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "job_id")
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_stage")
    private InterviewStage interviewStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type")
    private InterviewType interviewType;

    @Column(name = "estimated_difficulty")
    private String estimatedDifficulty;

    @Column(name = "preparation_priority")
    private String preparationPriority;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_required_technologies", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "technology")
    @Builder.Default
    private List<String> requiredTechnologies = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_required_concepts", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "concept")
    @Builder.Default
    private List<String> requiredConcepts = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_behavioral_topics", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "topic")
    @Builder.Default
    private List<String> behavioralTopics = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_coding_topics", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "topic")
    @Builder.Default
    private List<String> codingTopics = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_system_design_topics", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "topic")
    @Builder.Default
    private List<String> systemDesignTopics = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_preparation_checklist", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "item")
    @Builder.Default
    private List<String> preparationChecklist = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
