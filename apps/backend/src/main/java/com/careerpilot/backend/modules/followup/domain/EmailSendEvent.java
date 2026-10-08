package com.careerpilot.backend.modules.followup.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit event for draft approval and email sending. */
@Entity
@Table(name = "email_send_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendEvent {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(length = 20)
    private String provider;

    @Column(length = 320)
    private String recipient;

    @Column(length = 1000)
    private String detail;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
