package com.careerpilot.backend.modules.communication.api;

import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;

import java.time.Instant;
import java.util.UUID;

/**
 * Candidate-facing representation of a communication's classification. Exposes only server-owned
 * classification state; no request-supplied or model-supplied identifiers are ever echoed here.
 */
public record ClassificationResponse(
        UUID communicationId,
        CommunicationClassification classification,
        Double confidence,
        String evidence,
        CommunicationProcessingStatus processingStatus,
        Instant classifiedAt
) {

    public static ClassificationResponse from(HrCommunication c) {
        return new ClassificationResponse(
                c.getId(),
                c.getClassification(),
                c.getClassificationConfidence(),
                c.getClassificationReason(),
                c.getProcessingStatus(),
                c.getUpdatedAt()
        );
    }
}
