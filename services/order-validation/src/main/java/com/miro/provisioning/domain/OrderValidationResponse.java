package com.miro.provisioning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Validation decision returned to Workato. The field names are part of the
 * integration contract and are intentionally kept independent of internal
 * implementation classes.
 *
 * @param complianceChecks immutable audit markers for the rules that ran
 * @param correlationId identifier shared with response headers and server logs
 * @param processedAt timestamp of the original execution, preserved on replay
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
