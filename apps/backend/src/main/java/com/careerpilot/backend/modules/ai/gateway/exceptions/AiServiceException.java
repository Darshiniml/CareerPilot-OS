package com.careerpilot.backend.modules.ai.gateway.exceptions;

import com.careerpilot.backend.config.CodedException;

/**
 * Base failure for any call to the AI service. Carries the AI service's stable error {@code code}
 * (e.g. {@code AI_PROVIDER_NOT_CONFIGURED}) and the HTTP status the API should return, so failures
 * reach the user explicitly instead of being replaced by fabricated output.
 */
public class AiServiceException extends CodedException {

    public AiServiceException(String message) {
        this(message, "AI_SERVICE_ERROR", 502, null);
    }

    public AiServiceException(String message, Throwable cause) {
        this(message, "AI_SERVICE_ERROR", 502, cause);
    }

    public AiServiceException(String message, String code, int httpStatus, Throwable cause) {
        super(message, code, httpStatus, cause);
    }
}
