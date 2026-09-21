package com.careerpilot.backend.modules.communication.ingestion.security;

/**
 * Raised when a machine-to-machine ingestion request fails authentication (missing or incorrect
 * ingestion token).
 */
public class IngestionUnauthorizedException extends RuntimeException {

    public IngestionUnauthorizedException(String message) {
        super(message);
    }
}
