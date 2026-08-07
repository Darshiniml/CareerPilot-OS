package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class MemoryException extends AiServiceException {
    public MemoryException(String message) {
        super(message);
    }
    public MemoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
