package com.documind.exception;

/**
 * Base exception for failures during analysis job processing.
 */
public class JobExecutionException extends RuntimeException {

    public JobExecutionException(String message) {
        super(message);
    }

    public JobExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

