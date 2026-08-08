package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_progress")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningProgress {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false)
    private String skill;

    @Column(name = "progress_percentage", nullable = false)
    private double progressPercentage;

    @Column(nullable = false)
    private String status; // NOT_STARTED, IN_PROGRESS, COMPLETED

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
