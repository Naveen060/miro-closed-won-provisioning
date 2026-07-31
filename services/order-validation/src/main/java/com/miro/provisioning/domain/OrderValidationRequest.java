package com.miro.provisioning.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

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

