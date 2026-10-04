package com.careerpilot.backend.modules.application.domain;

import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent, provenance-preserving application timeline event derived from a classified HR
 * communication (Milestone 22.4).
 *
 * <p>Every row answers "what caused this application timeline event?": it references the exact
 * communication and its stored M22.3 classification (label, confidence, evidence) and records
 * whether a state transition resulted. The deterministic identity
 * {@code (application_id, communication_id, event_type)} makes repeated processing idempotent —
 * never keyed on timestamps or randomness. {@code event_timestamp} is the communication's
 * authoritative received time, never a fabricated value.</p>
 */
@Entity
@Table(name = "application_timeline_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_timeline_app_comm_event",
                columnNames = {"application_id", "communication_id", "event_type"}),
        indexes = {
                @Index(name = "idx_timeline_app_time", columnList = "application_id, event_timestamp")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationTimelineEvent {

    public static final String SOURCE_COMMUNICATION_CLASSIFICATION = "COMMUNICATION_CLASSIFICATION";

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "communication_id", nullable = false)
    private UUID communicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private TimelineEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 40, nullable = false)
    private TimelineEventOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 50)
    private WorkflowState previousState;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", length = 50)
    private WorkflowState newState;

    @Column(name = "state_changed", nullable = false)
    private boolean stateChanged;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 50, nullable = false)
    private CommunicationClassification classification;

    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @Column(name = "evidence", length = 2000)
    private String evidence;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "source", length = 40, nullable = false)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
