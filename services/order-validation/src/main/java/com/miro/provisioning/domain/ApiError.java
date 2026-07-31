package com.miro.provisioning.domain;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String message,
        List<String> details,
        String correlationId,
        Instant timestamp
) {
    public static ApiError of(String code, String message, List<String> details, String correlationId) {
        return new ApiError(code, message, List.copyOf(details), correlationId, Instant.now());
    }
}

