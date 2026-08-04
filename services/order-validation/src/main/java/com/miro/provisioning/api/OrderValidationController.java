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
/**
 * HTTP boundary used by Workato to validate a Closed Won order before any
 * downstream provisioning starts.
 *
 * <p>The controller intentionally remains thin: Bean Validation checks the
 * transport contract, while {@link OrderValidationService} owns idempotency
 * and business orchestration.</p>
 */
public class OrderValidationController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private final OrderValidationService orderValidationService;

    public OrderValidationController(OrderValidationService orderValidationService) {
        this.orderValidationService = orderValidationService;
    }

    @PostMapping("/validate")
    /**
     * Validates one order under the supplied idempotency key.
     *
     * @param idempotencyKey caller-owned key identifying one logical operation
     * @param request validated, PII-minimized order contract
     * @return the original or replayed decision plus replay response metadata
     */
    public ResponseEntity<OrderValidationResponse> validate(
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody OrderValidationRequest request
    ) {
        // CorrelationIdFilter establishes this value before the controller runs.
        IdempotentResult<OrderValidationResponse> result = orderValidationService.validate(
                request,
                idempotencyKey,
                MDC.get("correlationId")
        );

        // The replay signal is metadata rather than part of the decision body so
        // replayed responses remain byte-for-byte compatible with the original.
        return ResponseEntity.ok()
                .header(REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(result.value());
    }
}
