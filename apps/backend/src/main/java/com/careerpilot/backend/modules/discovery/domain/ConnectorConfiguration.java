package com.careerpilot.backend.modules.discovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "connector_configurations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorConfiguration {

    @Id
    @Column(name = "connector_id", length = 64)
    private String connectorId;

    @Column(name = "connector_name", nullable = false)
    private String connectorName;

    @Column(name = "connector_type", nullable = false, length = 50)
    private String connectorType; // DIRECT_ATS or AGGREGATOR

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "health_status", length = 50)
    private String healthStatus;

    @Column(name = "last_synchronization")
    private Instant lastSynchronization;

    @Column(name = "last_success")
    private Instant lastSuccess;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
