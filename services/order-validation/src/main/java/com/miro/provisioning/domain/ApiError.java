package com.miro.provisioning.domain;

import java.time.Instant;
import java.util.List;

/**
 * Consistent, PII-safe error envelope for all API failures.
 * Detailed exceptions remain in server logs and are never sent to callers.
 */
public record ApiError(
        String code,
        String message,
        List<String> details,
        String correlationId,
        Instant timestamp
) {
    /** Creates an immutable error snapshot stamped at the response boundary. */
    public static ApiError of(String code, String message, List<String> details, String correlationId) {
        // Defensive copying prevents later mutation of validation detail lists.
        return new ApiError(code, message, List.copyOf(details), correlationId, Instant.now());
    }
}
