package com.careerpilot.backend.modules.agent.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecution {

    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentExecutionStatus status;

    @Column(name = "payload", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "result", columnDefinition = "text")
    private String resultJson;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
