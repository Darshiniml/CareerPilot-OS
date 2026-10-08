package com.careerpilot.backend.modules.ai.gateway.exceptions;

/** The AI service answered with an error (provider not configured, unavailable, invalid output...). */
public class AiProviderException extends AiServiceException {
    public AiProviderException(String message) {
        super(message, "AI_PROVIDER_ERROR", 502, null);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, "AI_PROVIDER_ERROR", 502, cause);
    }

    public AiProviderException(String message, String code, int httpStatus) {
        super(message, code, httpStatus, null);
    }
}
