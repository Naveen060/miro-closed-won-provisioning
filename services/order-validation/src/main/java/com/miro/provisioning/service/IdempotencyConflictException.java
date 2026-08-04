package com.miro.provisioning.service;

/**
 * Raised when an existing idempotency key is reused for a different request.
 * Returning a conflict prevents a caller from receiving a stale success that
 * belongs to another payload.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("The Idempotency-Key was already used with a different request payload");
    }
}
