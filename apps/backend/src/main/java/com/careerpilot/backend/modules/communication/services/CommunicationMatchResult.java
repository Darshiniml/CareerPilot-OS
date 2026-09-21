package com.careerpilot.backend.modules.communication.services;

import java.util.UUID;

public record CommunicationMatchResult(
        boolean matched,
        UUID applicationId,
        Double confidence,
        String evidence
) {

    public static CommunicationMatchResult unmatched(String evidence) {
        return new CommunicationMatchResult(false, null, null, evidence);
    }

    public static CommunicationMatchResult matched(UUID applicationId, double confidence, String evidence) {
        return new CommunicationMatchResult(true, applicationId, confidence, evidence);
    }
}
