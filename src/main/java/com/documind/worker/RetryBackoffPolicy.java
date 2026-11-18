package com.documind.worker;

import com.documind.config.DocuMindProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Calculates exponential backoff durations for failed retryable jobs,
 * respecting explicit Retry-After headers, maximum caps, and jitter to avoid thundering herds.
 */
@Component
public class RetryBackoffPolicy {

    private final DocuMindProperties properties;

    public RetryBackoffPolicy(DocuMindProperties properties) {
        this.properties = properties;
    }

    /**
     * Computes the retry delay for a given attempt count and optional explicit retryAfter duration.
     *
     * @param attemptCount       The 1-based attempt count that just failed
     * @param explicitRetryAfter Optional explicit duration requested by remote service (e.g. Rate-limit)
     * @return Duration to wait before the next retry attempt
     */
    public Duration computeDelay(int attemptCount, Duration explicitRetryAfter) {
        if (explicitRetryAfter != null && !explicitRetryAfter.isNegative() && !explicitRetryAfter.isZero()) {
            return explicitRetryAfter;
        }

        long initialSeconds = Math.max(1, properties.getWorker().getBackoffInitialIntervalSeconds());
        double multiplier = Math.max(1.0, properties.getWorker().getBackoffMultiplier());
        long maxSeconds = Math.max(initialSeconds, properties.getWorker().getBackoffMaxIntervalSeconds());

        int effectiveAttempt = Math.max(1, attemptCount);
        double calculatedSeconds = initialSeconds * Math.pow(multiplier, effectiveAttempt - 1);
        long boundedSeconds = Math.min((long) calculatedSeconds, maxSeconds);

        // Add bounded jitter up to 15% to spread out concurrent retries
        long jitterMs = ThreadLocalRandom.current().nextLong(0, Math.max(1, boundedSeconds * 150));
        return Duration.ofMillis((boundedSeconds * 1000) + jitterMs);
    }

    /**
     * Deterministic version without jitter, useful for tests or predictable evaluations.
     */
    public Duration computeBaseDelay(int attemptCount) {
        long initialSeconds = Math.max(1, properties.getWorker().getBackoffInitialIntervalSeconds());
        double multiplier = Math.max(1.0, properties.getWorker().getBackoffMultiplier());
        long maxSeconds = Math.max(initialSeconds, properties.getWorker().getBackoffMaxIntervalSeconds());

        int effectiveAttempt = Math.max(1, attemptCount);
        double calculatedSeconds = initialSeconds * Math.pow(multiplier, effectiveAttempt - 1);
        long boundedSeconds = Math.min((long) calculatedSeconds, maxSeconds);
        return Duration.ofSeconds(boundedSeconds);
    }
}

