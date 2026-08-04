package com.miro.provisioning.service;

/**
 * Signals that the caller omitted the idempotency key or supplied a value that
 * exceeds the service contract. The API advice maps this client error to 400.
 */
public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
