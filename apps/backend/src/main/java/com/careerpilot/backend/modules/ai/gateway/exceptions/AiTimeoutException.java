package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class AiTimeoutException extends AiServiceException {
    public AiTimeoutException(String message) {
        super(message, "AI_TIMEOUT", 504, null);
    }
    public AiTimeoutException(String message, Throwable cause) {
        super(message, "AI_TIMEOUT", 504, cause);
    }
}
