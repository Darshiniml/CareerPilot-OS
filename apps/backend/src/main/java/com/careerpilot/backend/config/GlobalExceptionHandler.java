package com.careerpilot.backend.config;

import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), null, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed or missing request data", null, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(fieldName, error.getDefaultMessage());
        });
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", "Input validation errors occurred", errors, null);
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", "You do not have access to this resource", null, "FORBIDDEN");
    }

    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage() != null ? ex.getMessage() : "Not found", null, "NOT_FOUND");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), null, "CONFLICT");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", "File exceeds the maximum upload size (10 MB)", null, "UPLOAD_TOO_LARGE");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return build(status, status.getReasonPhrase(), ex.getReason(), null, null);
    }

    /** Explicit, coded failures (AI unavailable, email not configured...): never replaced by fabricated output. */
    @ExceptionHandler(CodedException.class)
    public ResponseEntity<ErrorResponse> handleCoded(CodedException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        log.warn("Coded failure code={} status={} message={}", ex.getCode(), status.value(), ex.getMessage());
        String error = ex instanceof AiServiceException ? "AI Unavailable" : status.getReasonPhrase();
        return build(status, error, ex.getMessage(), null, ex.getCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        // Internal details are logged, never returned to the client.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Reference: " + MDC.get("correlationId"), null, "INTERNAL_ERROR");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, Object details, String code) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .code(code)
                .message(message)
                .details(details)
                .correlationId(MDC.get("correlationId"))
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private Instant timestamp;
        private int status;
        private String error;
        private String code;
        private String message;
        private Object details;
        private String correlationId;
    }
}
