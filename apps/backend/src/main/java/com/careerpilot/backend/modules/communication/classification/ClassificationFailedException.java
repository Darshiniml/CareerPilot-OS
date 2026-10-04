package com.careerpilot.backend.modules.communication.classification;

/**
 * Raised when the AI classification output cannot be safely persisted: a missing or invalid
 * classification, a non-numeric or out-of-range confidence, missing evidence, or an unavailable
 * AI service. Processing fails safely — no fabricated classification is ever stored.
 */
public class ClassificationFailedException extends RuntimeException {

    public ClassificationFailedException(String message) {
        super(message);
    }

    public ClassificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
