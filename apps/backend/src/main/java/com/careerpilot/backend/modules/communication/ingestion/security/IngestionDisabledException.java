package com.careerpilot.backend.modules.communication.ingestion.security;

/**
 * Raised when inbound ingestion is not configured (no shared secret present). The ingestion
 * endpoint fails closed in this state rather than accepting unauthenticated machine traffic.
 */
public class IngestionDisabledException extends RuntimeException {

    public IngestionDisabledException(String message) {
        super(message);
    }
}
