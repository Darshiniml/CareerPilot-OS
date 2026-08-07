package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class AiTimeoutException extends AiServiceException {
    public AiTimeoutException(String message) {
        super(message);
    }
    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
