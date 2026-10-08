package com.careerpilot.backend.modules.analytics.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "learning_path_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPathItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_path_id", nullable = false)
    @JsonIgnore
    private LearningPath learningPath;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "prerequisite_steps")
    private String prerequisiteSteps;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    /** Resource suggestions by name and type only; links are never generated. */
    @Column(name = "resources_json", columnDefinition = "TEXT")
    private String resourcesJson; // Comma-separated
}
