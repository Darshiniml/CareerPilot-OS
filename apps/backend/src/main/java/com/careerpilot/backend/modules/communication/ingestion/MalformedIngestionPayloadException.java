package com.careerpilot.backend.modules.communication.ingestion;

/**
 * Raised when a provider payload is structurally present but semantically invalid (for example an
 * unparseable received timestamp). Mapped to HTTP 400 without leaking internal detail.
 */
public class MalformedIngestionPayloadException extends RuntimeException {

    public MalformedIngestionPayloadException(String message) {
        super(message);
    }
}
