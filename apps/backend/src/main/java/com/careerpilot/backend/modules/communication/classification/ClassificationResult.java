package com.careerpilot.backend.modules.communication.classification;

import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;

import java.util.List;

/**
 * Strict internal contract for a validated AI classification of an HR communication.
 *
 * <p>Carries only classification-owned values. Server-owned identifiers (candidateId,
 * applicationId, matchedApplicationId, applicationState, actorId) are intentionally absent
 * and can never be supplied by the model.</p>
 */
public record ClassificationResult(
        CommunicationClassification classification,
        double confidence,
        String evidence,
        String reason,
        List<String> signals
) {
}
