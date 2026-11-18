package com.documind.worker;

import com.documind.config.DocuMindProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryBackoffPolicyTest {

    private DocuMindProperties properties;
    private RetryBackoffPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new DocuMindProperties();
        properties.getWorker().setBackoffInitialIntervalSeconds(10);
        properties.getWorker().setBackoffMultiplier(2.0);
        properties.getWorker().setBackoffMaxIntervalSeconds(60);
        policy = new RetryBackoffPolicy(properties);
    }

    @Test
    @DisplayName("Exponential base backoff grows with each attempt")
    void testExponentialBaseBackoff() {
        assertEquals(Duration.ofSeconds(10), policy.computeBaseDelay(1));
        assertEquals(Duration.ofSeconds(20), policy.computeBaseDelay(2));
        assertEquals(Duration.ofSeconds(40), policy.computeBaseDelay(3));
        // Capped at max interval of 60 seconds
        assertEquals(Duration.ofSeconds(60), policy.computeBaseDelay(4));
        assertEquals(Duration.ofSeconds(60), policy.computeBaseDelay(10));
    }

    @Test
    @DisplayName("Explicit Retry-After duration takes precedence over calculated backoff")
    void testExplicitRetryAfterPrecedence() {
        Duration explicit = Duration.ofSeconds(45);
        Duration delay = policy.computeDelay(1, explicit);
        assertEquals(Duration.ofSeconds(45), delay);
    }

    @Test
    @DisplayName("Calculated delay with jitter is bounded within expected range")
    void testDelayWithJitter() {
        Duration delayAttempt1 = policy.computeDelay(1, null);
        // Base is 10s (10000ms), jitter is between 0 and 1500ms
        assertTrue(delayAttempt1.toMillis() >= 10000);
        assertTrue(delayAttempt1.toMillis() <= 12000);

        Duration delayAttempt2 = policy.computeDelay(2, null);
        // Base is 20s (20000ms), jitter is between 0 and 3000ms
        assertTrue(delayAttempt2.toMillis() >= 20000);
        assertTrue(delayAttempt2.toMillis() <= 24000);
    }
}

