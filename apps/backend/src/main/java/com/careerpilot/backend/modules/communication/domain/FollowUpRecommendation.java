package com.careerpilot.backend.modules.communication.domain;

import java.time.Instant;
import java.util.UUID;

public record FollowUpRecommendation(
        UUID communicationId,
        UUID applicationId,
        FollowUpAction recommendedAction,
        String rationale,
        Instant suggestedAt
) {
}
