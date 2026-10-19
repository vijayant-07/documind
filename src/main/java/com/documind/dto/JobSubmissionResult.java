package com.documind.dto;

import com.documind.domain.AnalysisJob;

public record JobSubmissionResult(
        AnalysisJob job,
        boolean newlyCreated
) {
}
