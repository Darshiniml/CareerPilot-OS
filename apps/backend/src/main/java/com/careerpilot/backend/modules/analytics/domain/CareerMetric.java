package com.careerpilot.backend.modules.analytics.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_metrics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerMetric {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "metric_type", nullable = false)
    private String metricType;

    @Column(name = "metric_value", nullable = false)
    private double metricValue;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();
}
