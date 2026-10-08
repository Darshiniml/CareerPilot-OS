package com.careerpilot.backend.modules.copilot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** One turn of a user's Copilot conversation (memory is strictly per user). */
@Entity
@Table(name = "copilot_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotMessage {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tools_json", columnDefinition = "TEXT")
    private String toolsJson;

    @Column(name = "citations_json", columnDefinition = "TEXT")
    private String citationsJson;

    @Column(name = "actions_json", columnDefinition = "TEXT")
    private String actionsJson;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
