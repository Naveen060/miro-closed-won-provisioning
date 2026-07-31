package com.miro.provisioning.api;

import com.miro.provisioning.domain.OrderValidationRequest;
import com.miro.provisioning.domain.OrderValidationResponse;
import com.miro.provisioning.service.IdempotentResult;
import com.miro.provisioning.service.OrderValidationService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderValidationController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private final OrderValidationService orderValidationService;

    public OrderValidationController(OrderValidationService orderValidationService) {
        this.orderValidationService = orderValidationService;
    }

    @PostMapping("/validate")
    public ResponseEntity<OrderValidationResponse> validate(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody OrderValidationRequest request
    ) {
        IdempotentResult<OrderValidationResponse> result = orderValidationService.validate(
                request,
                idempotencyKey,
                MDC.get("correlationId")
        );

        return ResponseEntity.ok()
                .header(REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(result.value());
    }
}
