package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationRequest;
import com.miro.provisioning.domain.OrderValidationResponse;

/**
 * Business-validation port. Keeping the rule engine behind this interface
 * allows the HTTP/idempotency layer to remain unchanged as rules evolve.
 */
public interface OrderValidator {

    OrderValidationResponse validate(OrderValidationRequest request, String correlationId);
}
