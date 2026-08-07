package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class PromptValidationException extends AiServiceException {
    public PromptValidationException(String message) {
        super(message);
    }
    public PromptValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
