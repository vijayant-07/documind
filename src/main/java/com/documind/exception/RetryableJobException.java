package com.documind.exception;

import java.time.Duration;

/**
 * Indicates a transient error that may succeed on a subsequent attempt
 * (e.g., temporary network failure, GitHub 5xx, rate limits, LLM timeout).
 */
public class RetryableJobException extends JobExecutionException {

    private final Duration retryAfter;

    public RetryableJobException(String message) {
        this(message, null, null);
    }

    public RetryableJobException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public RetryableJobException(String message, Duration retryAfter) {
        this(message, retryAfter, null);
    }

    public RetryableJobException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}

