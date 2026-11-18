package com.documind.exception;

/**
 * Indicates a deterministic, non-retryable failure that will not succeed
 * without code or input changes (e.g., missing repository permissions, PR not found,
 * unmerged PR, permanently malformed diff).
 */
public class PermanentJobException extends JobExecutionException {

    public PermanentJobException(String message) {
        super(message);
    }

    public PermanentJobException(String message, Throwable cause) {
        super(message, cause);
    }
}

