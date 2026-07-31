package com.miro.provisioning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderValidationResponse(
        String accountId,
        String validationStatus,
        BigDecimal totalAmount,
        String currency,
        String taxRoute,
        List<String> complianceChecks,
        String correlationId,
        Instant processedAt
) {
}

