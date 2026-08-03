package com.miro.provisioning.service;

/** Result value plus an explicit indication that it came from the idempotency cache. */
public record IdempotentResult<T>(T value, boolean replayed) {
}
