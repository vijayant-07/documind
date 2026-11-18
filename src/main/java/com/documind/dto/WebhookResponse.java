package com.documind.dto;

public record WebhookResponse(
        String status,
        String message,
        Long jobId,
        String idempotencyKey
) {
    public static WebhookResponse accepted(Long jobId, String idempotencyKey) {
        return new WebhookResponse(
                "ACCEPTED",
                "Qualifying pull request merge event accepted for documentation analysis",
                jobId,
                idempotencyKey
        );
    }

    public static WebhookResponse duplicate(Long jobId, String idempotencyKey) {
        return new WebhookResponse(
                "DUPLICATE",
                "Duplicate webhook delivery; job already exists",
                jobId,
                idempotencyKey
        );
    }

    public static WebhookResponse ignored(String reason) {
        return new WebhookResponse(
                "IGNORED",
                reason,
                null,
                null
        );
    }
}
