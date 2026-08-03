package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class InMemoryIdempotencyService {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public IdempotentResult<OrderValidationResponse> execute(
            String key,
            String requestFingerprint,
            Supplier<OrderValidationResponse> operation
    ) {
        validateKey(key);

        // putIfAbsent elects exactly one caller to execute the operation. Other
        // concurrent callers wait on the same future and receive the same response.
        Entry candidate = new Entry(requestFingerprint, new CompletableFuture<>());
        Entry existing = entries.putIfAbsent(key, candidate);
        if (existing != null) {
            ensureSameRequest(existing, requestFingerprint);
            return new IdempotentResult<>(await(existing.result()), true);
        }

        try {
            OrderValidationResponse response = operation.get();
            candidate.result().complete(response);
            return new IdempotentResult<>(response, false);
        } catch (RuntimeException | Error error) {
            candidate.result().completeExceptionally(error);
            entries.remove(key, candidate);
            throw error;
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must not be blank");
        }
        if (key.length() > 200) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must be at most 200 characters");
        }
    }

    private static void ensureSameRequest(Entry entry, String requestFingerprint) {
        if (!entry.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException();
        }
    }

    private static OrderValidationResponse await(CompletableFuture<OrderValidationResponse> result) {
        try {
            return result.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw error;
        }
    }

    private record Entry(
            String requestFingerprint,
            CompletableFuture<OrderValidationResponse> result
    ) {
    }
}
