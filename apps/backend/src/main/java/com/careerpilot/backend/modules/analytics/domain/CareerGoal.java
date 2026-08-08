package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerGoal {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "target_role", nullable = false)
    private String targetRole;

    @Column(name = "target_industry")
    private String targetIndustry;

    @Column(name = "target_salary")
    private Double targetSalary;

    @Column(name = "target_location")
    private String targetLocation;

    @Column(name = "timeline_months")
    private Integer timelineMonths;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
