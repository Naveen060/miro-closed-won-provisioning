package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationRequest;
import com.miro.provisioning.domain.OrderValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
/**
 * Application service that connects request fingerprinting, idempotent
 * execution, and the business validator. It is the transaction boundary used
 * by the HTTP controller and keeps transport concerns out of validation rules.
 */
public class OrderValidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderValidationService.class);

    private final InMemoryIdempotencyService idempotencyService;
    private final RequestFingerprint requestFingerprint;
    private final OrderValidator orderValidator;

    public OrderValidationService(
            InMemoryIdempotencyService idempotencyService,
            RequestFingerprint requestFingerprint,
            OrderValidator orderValidator
    ) {
        this.idempotencyService = idempotencyService;
        this.requestFingerprint = requestFingerprint;
        this.orderValidator = orderValidator;
    }

    public IdempotentResult<OrderValidationResponse> validate(
            OrderValidationRequest request,
            String idempotencyKey,
            String correlationId
    ) {
        // Fingerprint before executing business logic so the same key cannot be
        // used to retrieve a response generated for a different order payload.
        String fingerprint = requestFingerprint.forRequest(request);
        // The supplier is invoked only for the caller that wins key ownership;
        // concurrent and later duplicates receive the stored response instead.
        IdempotentResult<OrderValidationResponse> result = idempotencyService.execute(
                idempotencyKey,
                fingerprint,
                () -> orderValidator.validate(request, correlationId)
        );

        // Correlation data is supplied through MDC by CorrelationIdFilter, so the
        // message can stay PII-safe while remaining traceable across services.
        LOGGER.info("order_validation status={} replayed={}",
                result.value().validationStatus(), result.replayed());
        return result;
    }
}
