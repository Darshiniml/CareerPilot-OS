package com.careerpilot.backend.modules.followup.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An AI-generated follow-up email draft. Lifecycle:
 * DRAFT → APPROVED (explicit user approval) → SENDING → SENT | FAILED; or DISCARDED.
 * Editing an approved draft returns it to DRAFT so every sent text was approved as-is.
 */
@Entity
@Table(name = "follow_up_drafts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpDraft {
    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";
    public static final String DISCARDED = "DISCARDED";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "communication_id")
    private UUID communicationId;

    @Column(name = "recommendation_key", length = 200)
    private String recommendationKey;

    @Column(name = "draft_type", nullable = false, length = 50)
    private String draftType;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "placeholders_json", columnDefinition = "TEXT")
    private String placeholdersJson;

    @Column(name = "verification_json", columnDefinition = "TEXT")
    private String verificationJson;

    @Column(name = "edited_by_user", nullable = false)
    private boolean editedByUser;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 320)
    private String recipient;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "send_error", length = 1000)
    private String sendError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
