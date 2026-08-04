package com.miro.provisioning.service;

/**
 * Wraps a service result with replay metadata. The controller translates the
 * flag into the {@code Idempotency-Replayed} response header while returning
 * the same response body for both original and replayed requests.
 */
public record IdempotentResult<T>(T value, boolean replayed) {
}
