package com.careerpilot.backend.modules.communication.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hr_communications",
        uniqueConstraints = @UniqueConstraint(name = "uk_hr_comm_provider_external",
                columnNames = {"provider", "external_message_id"}),
        indexes = {
                @Index(name = "idx_hr_comm_candidate_received", columnList = "candidate_id, received_at"),
                @Index(name = "idx_hr_comm_matched_application", columnList = "matched_application_id"),
                @Index(name = "idx_hr_comm_external_message", columnList = "external_message_id"),
                @Index(name = "idx_hr_comm_thread", columnList = "thread_id"),
                @Index(name = "idx_hr_comm_received_at", columnList = "received_at"),
                @Index(name = "idx_hr_comm_processing_status", columnList = "processing_status")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrCommunication {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30, nullable = false)
    private CommunicationProvider provider;

    @Column(name = "external_message_id", nullable = false)
    private String externalMessageId;

    @Column(name = "thread_id")
    private String threadId;

    @Column(name = "sender", nullable = false, length = 320)
    private String sender;

    @Column(name = "recipient", length = 320)
    private String recipient;

    @Column(name = "subject", length = 1000)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "matched_application_id")
    private UUID matchedApplicationId;

    @Column(name = "match_confidence")
    private Double matchConfidence;

    @Column(name = "match_evidence", length = 2000)
    private String matchEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 50, nullable = false)
    @Builder.Default
    private CommunicationClassification classification = CommunicationClassification.UNKNOWN;

    @Column(name = "classification_confidence")
    private Double classificationConfidence;

    @Column(name = "classification_reason", length = 2000)
    private String classificationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 30, nullable = false)
    @Builder.Default
    private CommunicationProcessingStatus processingStatus = CommunicationProcessingStatus.RECEIVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;
}
