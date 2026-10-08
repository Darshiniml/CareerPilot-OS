package com.careerpilot.backend.config;

/**
 * A failure with a stable machine-readable {@code code} and the HTTP status the API should return.
 * Used for explicit, user-visible failures (AI unavailable, email not configured, ...).
 */
public class CodedException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public CodedException(String message, String code, int httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
