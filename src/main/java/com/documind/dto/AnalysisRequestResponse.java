package com.documind.dto;

import java.time.Instant;

public record AnalysisRequestResponse(
        String status,
        String message,
        String owner,
        String repository,
        int pullRequestNumber,
        Instant receivedAt
) {
}
