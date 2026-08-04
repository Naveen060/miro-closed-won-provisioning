package com.miro.provisioning.service;

import com.miro.provisioning.domain.OrderValidationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotencyServiceTest {

    // Use more workers than a typical request burst to exercise the atomic
    // winner-selection path rather than a merely sequential cache lookup.
    private final ExecutorService executor = Executors.newFixedThreadPool(24);

    @AfterEach
    void shutDownExecutor() throws InterruptedException {
        // Prevent test worker threads from leaking into later Maven tests.
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void executesOnlyOnceWhenIdenticalRequestsArriveConcurrently() throws Exception {
        InMemoryIdempotencyService service = new InMemoryIdempotencyService();
        AtomicInteger operationCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(24);
        CountDownLatch start = new CountDownLatch(1);

        Callable<IdempotentResult<OrderValidationResponse>> request = () -> {
            // Signal that every worker exists, then release them together to
            // maximize contention for the same idempotency-key map entry.
            ready.countDown();
            start.await(2, TimeUnit.SECONDS);
            return service.execute("same-key", "same-fingerprint", () -> {
                operationCount.incrementAndGet();
                try {
                    Thread.sleep(75);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return response();
            });
        };

        List<Future<IdempotentResult<OrderValidationResponse>>> futures = IntStream.range(0, 24)
                .mapToObj(ignored -> executor.submit(request))
                .toList();
        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<IdempotentResult<OrderValidationResponse>> results = futures.stream()
                .map(InMemoryIdempotencyServiceTest::get)
                .toList();

        // Exactly one original response and 23 replay flags prove that callers
        // shared one completed future rather than performing duplicate work.
        assertThat(operationCount).hasValue(1);
        assertThat(results).extracting(result -> result.value().processedAt()).containsOnly(Instant.EPOCH);
        assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
        assertThat(results).filteredOn(IdempotentResult::replayed).hasSize(23);
    }

    @Test
    void doesNotCacheFailuresSoTheRequestCanBeRetried() {
        InMemoryIdempotencyService service = new InMemoryIdempotencyService();
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> service.execute("retry-key", "fingerprint", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("transient failure");
        })).isInstanceOf(IllegalStateException.class);

        // The same key/fingerprint must be allowed to execute after a transient
        // failure; otherwise a temporary outage would poison the key forever.
        IdempotentResult<OrderValidationResponse> retry = service.execute(
                "retry-key",
                "fingerprint",
                () -> {
                    attempts.incrementAndGet();
                    return response();
                }
        );

        assertThat(attempts).hasValue(2);
        assertThat(retry.replayed()).isFalse();
    }

    @Test
    void rejectsTheSameKeyWithADifferentFingerprint() {
        InMemoryIdempotencyService service = new InMemoryIdempotencyService();
        service.execute("one-key", "first", InMemoryIdempotencyServiceTest::response);

        assertThatThrownBy(() -> service.execute("one-key", "second", InMemoryIdempotencyServiceTest::response))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    private static <T> T get(Future<T> future) {
        // Apply a bounded wait so a concurrency regression fails rather than hangs.
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static OrderValidationResponse response() {
        // A fixed timestamp makes exact-response sharing easy to assert.
        return new OrderValidationResponse(
                "acct-123",
                "VALID",
                new BigDecimal("25.00"),
                "USD",
                "US_STATE_SALES_TAX",
                List.of("REQUIRED_FIELDS_PASSED"),
                "corr-123",
                Instant.EPOCH
        );
    }
}
