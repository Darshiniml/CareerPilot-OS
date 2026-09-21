package com.careerpilot.backend.modules.communication.api;

import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;

import java.time.Instant;
import java.util.UUID;

public record CommunicationResponse(
        UUID id,
        UUID candidateId,
        CommunicationProvider provider,
        String externalMessageId,
        String threadId,
        String sender,
        String recipient,
        String subject,
        String body,
        Instant receivedAt,
        UUID matchedApplicationId,
        Double matchConfidence,
        String matchEvidence,
        CommunicationClassification classification,
        Double classificationConfidence,
        String classificationReason,
        CommunicationProcessingStatus processingStatus,
        Instant createdAt,
        Instant updatedAt
) {

    public static CommunicationResponse from(HrCommunication c) {
        return new CommunicationResponse(
                c.getId(),
                c.getCandidateId(),
                c.getProvider(),
                c.getExternalMessageId(),
                c.getThreadId(),
                c.getSender(),
                c.getRecipient(),
                c.getSubject(),
                c.getBody(),
                c.getReceivedAt(),
                c.getMatchedApplicationId(),
                c.getMatchConfidence(),
                c.getMatchEvidence(),
                c.getClassification(),
                c.getClassificationConfidence(),
                c.getClassificationReason(),
                c.getProcessingStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
