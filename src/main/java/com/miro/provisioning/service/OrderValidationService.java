package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationRequest;
import com.miro.provisioning.domain.OrderValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
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
        String fingerprint = requestFingerprint.forRequest(request);
        IdempotentResult<OrderValidationResponse> result = idempotencyService.execute(
                idempotencyKey,
                fingerprint,
                () -> orderValidator.validate(request, correlationId)
        );

        LOGGER.info("order_validation status={} replayed={}",
                result.value().validationStatus(), result.replayed());
        return result;
    }
}

