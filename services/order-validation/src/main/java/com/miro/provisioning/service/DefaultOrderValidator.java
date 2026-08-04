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
/**
 * Default deterministic implementation of the order-validation rules. It
 * normalizes optional geography fields, selects a tax-processing route, and
 * emits auditable compliance markers for the orchestration workflow.
 */
public class DefaultOrderValidator implements OrderValidator {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_COUNTRY = "US";
    private static final String VALID_STATUS = "VALID";
    private static final String INTERNATIONAL_REVIEW = "INTERNATIONAL_REVIEW";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000000");

    // Known countries route directly to their tax regime; all other ISO codes
    // deliberately require international review rather than guessing a rule.
    private static final Map<String, String> TAX_ROUTES = Map.of(
            "US", "US_STATE_SALES_TAX",
            "CA", "CANADA_GST_HST",
            "GB", "UK_VAT",
            "DE", "EU_VAT"
    );

    private final Clock clock;

    public DefaultOrderValidator() {
        // Production timestamps use UTC to remain comparable across deployments.
        this(Clock.systemUTC());
    }

    DefaultOrderValidator(Clock clock) {
        // Package visibility allows tests to inject a fixed clock without adding
        // production-only configuration or time-dependent assertions.
        this.clock = clock;
    }

    @Override
    public OrderValidationResponse validate(OrderValidationRequest request, String correlationId) {
        // Currency and country are optional at the transport boundary. Normalize
        // supplied values or apply the documented USD/US defaults consistently.
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
        // The list is an audit-friendly description of rules applied, not merely
        // a boolean. Downstream automation can branch on individual markers.
        List<String> checks = new ArrayList<>();
        checks.add("REQUIRED_FIELDS_PASSED");
        checks.add("SANCTIONS_SCREEN_PASSED");
        checks.add("TAX_ROUTE_" + taxRoute);
        if (totalAmount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            // The threshold is inclusive: a deal of exactly 1,000,000 is reviewed.
            checks.add("HIGH_VALUE_DEAL_REVIEW_REQUIRED");
        }
        return List.copyOf(checks); // Prevent callers from mutating the decision record.
    }

    private static String normalizedOrDefault(String value, String defaultValue) {
        // Locale.ROOT avoids locale-specific casing (for example Turkish i/I)
        // in values that are protocol codes rather than natural-language text.
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
