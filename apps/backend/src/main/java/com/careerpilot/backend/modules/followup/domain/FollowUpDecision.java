package com.careerpilot.backend.modules.followup.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** A user's decision on a follow-up recommendation (dismissed, snoozed or done). */
@Entity
@Table(name = "follow_up_decisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpDecision {
    public static final String DISMISSED = "DISMISSED";
    public static final String SNOOZED = "SNOOZED";
    public static final String DONE = "DONE";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recommendation_key", nullable = false, length = 200)
    private String recommendationKey;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(name = "snooze_until")
    private Instant snoozeUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
