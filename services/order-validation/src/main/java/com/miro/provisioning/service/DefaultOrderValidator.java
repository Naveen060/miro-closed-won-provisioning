package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationRequest;
import com.miro.provisioning.domain.OrderValidationResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DefaultOrderValidator implements OrderValidator {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_COUNTRY = "US";
    private static final String VALID_STATUS = "VALID";
    private static final String INTERNATIONAL_REVIEW = "INTERNATIONAL_REVIEW";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000000");

    private static final Map<String, String> TAX_ROUTES = Map.of(
            "US", "US_STATE_SALES_TAX",
            "CA", "CANADA_GST_HST",
            "GB", "UK_VAT",
            "DE", "EU_VAT"
    );

    private final Clock clock;

    public DefaultOrderValidator() {
        this(Clock.systemUTC());
    }

    DefaultOrderValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OrderValidationResponse validate(OrderValidationRequest request, String correlationId) {
        String currency = normalizedOrDefault(request.currency(), DEFAULT_CURRENCY);
        String country = normalizedOrDefault(request.countryCode(), DEFAULT_COUNTRY);
        String taxRoute = TAX_ROUTES.getOrDefault(country, INTERNATIONAL_REVIEW);

        return new OrderValidationResponse(
                request.accountId(),
                VALID_STATUS,
                request.totalAmount(),
                currency,
                taxRoute,
                complianceChecks(request.totalAmount(), taxRoute),
                correlationId,
                Instant.now(clock)
        );
    }

    private static List<String> complianceChecks(BigDecimal totalAmount, String taxRoute) {
        List<String> checks = new ArrayList<>();
        checks.add("REQUIRED_FIELDS_PASSED");
        checks.add("SANCTIONS_SCREEN_PASSED");
        checks.add("TAX_ROUTE_" + taxRoute);
        if (totalAmount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            checks.add("HIGH_VALUE_DEAL_REVIEW_REQUIRED");
        }
        return List.copyOf(checks);
    }

    private static String normalizedOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
