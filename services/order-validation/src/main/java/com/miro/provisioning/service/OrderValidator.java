package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationRequest;
import com.miro.provisioning.domain.OrderValidationResponse;

public interface OrderValidator {

    OrderValidationResponse validate(OrderValidationRequest request, String correlationId);
}

