package com.miro.provisioning.service;

public record IdempotentResult<T>(T value, boolean replayed) {
}

