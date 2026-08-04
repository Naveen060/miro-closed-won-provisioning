package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
/**
 * Process-local idempotency coordinator for order validation requests.
 *
 * <p>Each key owns a fingerprint and a future. The future lets concurrent
 * duplicates share one in-flight computation, while the fingerprint prevents
 * a key from being reused for a different payload. Successful results remain
 * cached for the lifetime of this service instance; failures are removed so a
 * later retry can execute again.</p>
 */
public class InMemoryIdempotencyService {

    // ConcurrentHashMap makes key election atomic without serializing unrelated keys.
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
            // Only the thread that inserted candidate reaches this operation.
            OrderValidationResponse response = operation.get();
            // Complete before returning so all waiting duplicates observe the
            // exact same immutable response, including its processed timestamp.
            candidate.result().complete(response);
            return new IdempotentResult<>(response, false);
        } catch (RuntimeException | Error error) {
            // Wake current waiters with the same failure, then remove only our
            // candidate entry so a later request may retry the transient work.
            candidate.result().completeExceptionally(error);
            entries.remove(key, candidate);
            throw error;
        }
    }

    private static void validateKey(String key) {
        // Bound key size to avoid unbounded attacker-controlled map metadata.
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
            // Preserve application exception types so the global API handler can
            // apply the same response mapping to original and duplicate callers.
            if (error.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw error;
        }
    }

    private record Entry(
            // The fingerprint establishes payload identity; the future represents
            // either an in-flight operation or its completed immutable response.
            String requestFingerprint,
            CompletableFuture<OrderValidationResponse> result
    ) {
    }
}
