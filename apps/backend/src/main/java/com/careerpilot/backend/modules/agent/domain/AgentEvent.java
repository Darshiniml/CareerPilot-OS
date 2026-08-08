package com.careerpilot.backend.modules.agent.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    @Id
    private UUID id;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Convert(converter = com.careerpilot.backend.config.JsonMapConverter.class)
    @Column(name = "payload", columnDefinition = "text", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
