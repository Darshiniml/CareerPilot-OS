package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "learning_paths")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPath {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private String skill;

    @Column(name = "current_level", nullable = false)
    private String currentLevel;

    @Column(name = "target_level", nullable = false)
    private String targetLevel;

    @Column(nullable = false)
    private String priority;

    @Column(name = "estimated_hours", nullable = false)
    private int estimatedHours;

    @Column(name = "expected_match_improvement", nullable = false)
    private double expectedMatchImprovement;

    @OneToMany(mappedBy = "learningPath", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LearningPathItem> learningSequence = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
