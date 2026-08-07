package com.careerpilot.backend.modules.ai.task.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTask {

    @Id
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private AiWorkflow workflow;
    
    @Column(name = "task_type", nullable = false)
    private String taskType;
    
    @Column(nullable = false)
    @Builder.Default
    private String status = "CREATED"; // CREATED, QUEUED, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED
    
    @Column(nullable = false)
    @Builder.Default
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH
    
    @Column(name = "correlation_id")
    private String correlationId;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "assigned_agent")
    private String assignedAgent;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
