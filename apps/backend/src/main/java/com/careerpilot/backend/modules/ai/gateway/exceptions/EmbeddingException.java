package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class EmbeddingException extends AiServiceException {
    public EmbeddingException(String message) {
        super(message);
    }
    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
