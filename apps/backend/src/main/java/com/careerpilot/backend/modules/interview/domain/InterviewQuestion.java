package com.careerpilot.backend.modules.interview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "category")
    private String category;

    @Column(name = "candidate_answer")
    private String candidateAnswer;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

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

    @Column(name = "evaluation_feedback")
    private String evaluationFeedback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
