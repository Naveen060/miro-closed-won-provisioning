package com.miro.provisioning.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Stable request contract accepted from Workato.
 *
 * <p>Only fields required to make a validation decision are accepted. This
 * keeps customer PII out of the validation service and its diagnostic data.</p>
 */
public record OrderValidationRequest(
        @NotBlank(message = "accountId is required")
        String accountId,

        @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than zero")
        BigDecimal totalAmount,

        String currency,
        String countryCode,
        String opportunityId
) {
}
