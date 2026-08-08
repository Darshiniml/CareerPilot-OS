package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_demand_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDemandSnapshot {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String skill;

    @Column(name = "demand_percentage", nullable = false)
    private double demandPercentage;

    @Column(name = "captured_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant capturedAt = Instant.now();
}
