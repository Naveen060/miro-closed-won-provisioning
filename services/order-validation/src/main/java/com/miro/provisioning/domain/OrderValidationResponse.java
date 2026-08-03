package com.miro.provisioning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Validation decision returned to Workato. The field names are part of the
 * integration contract and are intentionally kept independent of internal
 * implementation classes.
 */
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
