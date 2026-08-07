package com.careerpilot.backend.modules.ai.gateway.exceptions;

public class WorkflowException extends AiServiceException {
    public WorkflowException(String message) {
        super(message);
    }
    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
