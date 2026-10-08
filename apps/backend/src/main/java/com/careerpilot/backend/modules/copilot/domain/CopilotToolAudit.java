package com.careerpilot.backend.modules.copilot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Audit record of a single Copilot tool invocation. */
@Entity
@Table(name = "copilot_tool_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotToolAudit {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "tool_name", nullable = false, length = 80)
    private String toolName;

    @Column(name = "arguments_json", columnDefinition = "TEXT")
    private String argumentsJson;

    /** MODEL when the LLM planner chose the tool, FALLBACK when keyword routing did. */
    @Column(name = "selected_by", nullable = false, length = 20)
    private String selectedBy;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
