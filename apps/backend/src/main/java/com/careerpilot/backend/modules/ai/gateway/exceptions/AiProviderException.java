package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class AiProviderException extends AiServiceException {
    public AiProviderException(String message) {
        super(message);
    }
    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
