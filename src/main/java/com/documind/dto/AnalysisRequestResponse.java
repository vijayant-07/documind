package com.documind.dto;

import java.time.Instant;

public record AnalysisRequestResponse(
        String status,
        String message,
        Long jobId,
        String owner,
        String repository,
        int pullRequestNumber,
        Instant receivedAt
) {
    public AnalysisRequestResponse(
            String status,
            String message,
            String owner,
            String repository,
            int pullRequestNumber,
            Instant receivedAt
    ) {
        this(status, message, null, owner, repository, pullRequestNumber, receivedAt);
    }
}
