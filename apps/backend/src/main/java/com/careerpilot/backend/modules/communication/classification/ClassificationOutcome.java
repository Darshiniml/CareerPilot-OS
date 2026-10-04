package com.careerpilot.backend.modules.communication.classification;

import com.careerpilot.backend.modules.communication.domain.HrCommunication;

/**
 * Result of a classification attempt. On success the communication carries the validated
 * classification and {@code processingStatus == PROCESSED}. On AI failure the communication is
 * persisted with {@code processingStatus == FAILED}, no fabricated classification is stored, and
 * {@code failureReason} carries an honest description for the API response.
 */
public record ClassificationOutcome(HrCommunication communication, String failureReason) {

    public boolean success() {
        return failureReason == null;
    }

    public static ClassificationOutcome success(HrCommunication communication) {
        return new ClassificationOutcome(communication, null);
    }

    public static ClassificationOutcome failure(HrCommunication communication, String failureReason) {
        return new ClassificationOutcome(communication, failureReason);
    }
}
