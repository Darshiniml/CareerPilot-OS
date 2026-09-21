package com.careerpilot.backend.modules.communication.services;

/**
 * Raised when an inbound HR communication carries a provider/external-message identity that
 * already belongs to a different candidate. The controller maps this to HTTP 409.
 */
public class DuplicateCommunicationException extends RuntimeException {

    public DuplicateCommunicationException(String message) {
        super(message);
    }
}
