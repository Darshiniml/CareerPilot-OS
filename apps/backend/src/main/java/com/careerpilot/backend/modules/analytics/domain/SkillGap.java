package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_gaps")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillGap {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private String skill;

    @Column(name = "demand_percentage", nullable = false)
    private double demandPercentage;

    @Column(name = "current_level", nullable = false)
    private String currentLevel; // BEGINNER, INTERMEDIATE, ADVANCED, MISSING

    @Column(name = "match_improvement_potential", nullable = false)
    private double matchImprovementPotential;

    @Column(nullable = false)
    private String priority; // HIGH, MEDIUM, LOW

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
