package com.miro.provisioning.api;

import com.miro.provisioning.domain.ApiError;
import com.miro.provisioning.service.IdempotencyConflictException;
import com.miro.provisioning.service.InvalidIdempotencyKeyException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
/**
 * Central translation layer from framework and domain exceptions to the
 * service's stable, PII-safe JSON error contract.
 */
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException error) {
        // Sort field details to keep responses deterministic across JVM or
        // framework ordering differences, which simplifies automation and tests.
        List<String> details = error.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted()
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, InvalidIdempotencyKeyException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception error) {
        // These failures all describe correctable caller input and therefore
        // share the generic BAD_REQUEST classification.
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", error.getMessage(), List.of());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleConflict(IdempotencyConflictException error) {
        // HTTP 409 communicates that the key already names another payload.
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", error.getMessage(), List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException error) {
        // Do not expose parser details because they can reveal implementation
        // types and are not needed for the caller to correct malformed JSON.
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body must contain valid JSON", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedFailure(Exception error) {
        // Log the stack trace internally, but never return implementation details to the caller.
        LOGGER.error("Unhandled API failure correlationId={}", MDC.get("correlationId"), error);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            List<String> details
    ) {
        // Read correlationId from MDC so every error, including framework errors
        // raised before the controller, can be matched to response headers/logs.
        return ResponseEntity.status(status).body(
                ApiError.of(code, message, details, MDC.get("correlationId"))
        );
    }
}
