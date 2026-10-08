package com.careerpilot.backend.modules.interview.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One practice question in an interview session. Questions are AI-generated practice questions
 * ({@code provenance}); scores are the AI rubric evaluation of the candidate's real answer, with the
 * overall score computed by CareerPilot. Unanswered questions have no scores.
 */
@Entity
@Table(name = "interview_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {
    @Id
    private UUID questionId;

    @Column(name = "question_order")
    private Integer questionOrder;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "category")
    private String category;

    @Column(name = "question_type", length = 30)
    private String questionType;

    @Column(name = "skill_area")
    private String skillArea;

    @Column(name = "difficulty", length = 10)
    private String difficulty;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @JsonIgnore
    @Column(name = "evaluation_criteria_json", columnDefinition = "TEXT")
    private String evaluationCriteriaJson;

    @Column(name = "provenance", length = 60)
    private String provenance;

    @Column(name = "candidate_answer", columnDefinition = "TEXT")
    private String candidateAnswer;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "correctness_score")
    private Double correctnessScore;

    @Column(name = "completeness_score")
    private Double completenessScore;

    @Column(name = "technical_accuracy_score")
    private Double technicalAccuracyScore;

    @Column(name = "communication_score")
    private Double communicationScore;

    @Column(name = "examples_score")
    private Double examplesScore;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "structure_score")
    private Double structureScore;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "clarity_score")
    private Double clarityScore;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "evaluation_feedback", columnDefinition = "TEXT")
    private String evaluationFeedback;

    /** Full AI evaluation (strengths, improvements, missing points, model answer outline). */
    @Column(name = "evaluation_json", columnDefinition = "TEXT")
    private String evaluationJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
