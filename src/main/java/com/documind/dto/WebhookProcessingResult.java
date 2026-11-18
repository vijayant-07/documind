package com.documind.dto;

import com.documind.domain.AnalysisJob;

public record WebhookProcessingResult(
        ResultType type,
        String message,
        AnalysisJob job
) {
    public enum ResultType {
        ACCEPTED,
        DUPLICATE,
        IGNORED
    }

    public static WebhookProcessingResult accepted(AnalysisJob job) {
        return new WebhookProcessingResult(
                ResultType.ACCEPTED,
                "Qualifying pull request merge event accepted for documentation analysis",
                job
        );
    }

    public static WebhookProcessingResult duplicate(AnalysisJob job) {
        return new WebhookProcessingResult(
                ResultType.DUPLICATE,
                "Duplicate webhook delivery; job already exists",
                job
        );
    }

    public static WebhookProcessingResult ignored(String reason) {
        return new WebhookProcessingResult(
                ResultType.IGNORED,
                reason,
                null
        );
    }
}
