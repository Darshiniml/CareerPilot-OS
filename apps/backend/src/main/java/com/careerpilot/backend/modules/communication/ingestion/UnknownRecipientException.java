package com.careerpilot.backend.modules.communication.ingestion;

/**
 * Raised when an authenticated inbound communication names a recipient that does not correspond to
 * any CareerPilot candidate. Candidate identity is always derived server-side from the recipient
 * address; an unknown recipient is rejected rather than attached to a fallback account.
 */
public class UnknownRecipientException extends RuntimeException {

    public UnknownRecipientException(String message) {
        super(message);
    }
}
