package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_goal_progress")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerGoalProgress {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "current_state", columnDefinition = "TEXT")
    private String currentState;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills; // Comma-separated or JSON

    @Column(name = "required_experience", columnDefinition = "TEXT")
    private String requiredExperience; // Comma-separated or text

    @Column(name = "required_projects", columnDefinition = "TEXT")
    private String requiredProjects; // Comma-separated or text

    @Column(name = "learning_plan_progress", nullable = false)
    private double learningPlanProgress;

    @Column(name = "overall_progress", nullable = false)
    private double overallProgress;

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
