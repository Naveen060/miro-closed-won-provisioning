package com.miro.provisioning.api;

import com.miro.provisioning.domain.ApiError;
import com.miro.provisioning.service.IdempotencyConflictException;
import com.miro.provisioning.service.InvalidIdempotencyKeyException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException error) {
        List<String> details = error.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted()
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, InvalidIdempotencyKeyException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception error) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", error.getMessage(), List.of());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleConflict(IdempotencyConflictException error) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", error.getMessage(), List.of());
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            List<String> details
    ) {
        return ResponseEntity.status(status).body(
                ApiError.of(code, message, details, MDC.get("correlationId"))
        );
    }
}
