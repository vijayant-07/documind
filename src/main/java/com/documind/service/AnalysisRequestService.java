package com.documind.service;

import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.dto.PullRequestAnalysisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AnalysisRequestService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRequestService.class);

    private final AnalysisJobService analysisJobService;

    public AnalysisRequestService(AnalysisJobService analysisJobService) {
        this.analysisJobService = analysisJobService;
    }

    /**
     * Accepts, validates, and persists a manual PR analysis request idempotently.
     */
    public AnalysisRequestResponse processAnalysisRequest(PullRequestAnalysisRequest request) {
        String repoFullName = request.owner() + "/" + request.repository();
        String idempotencyKey = JobCreateCommand.buildManualIdempotencyKey(repoFullName, request.pullRequestNumber());

        JobCreateCommand command = new JobCreateCommand(
                null,
                repoFullName,
                request.pullRequestNumber(),
                null,
                null,
                null,
                idempotencyKey,
                null
        );

        JobSubmissionResult result = analysisJobService.createOrGetJob(command);

        String message = result.newlyCreated()
                ? "Analysis request accepted for processing"
                : "Analysis job already exists for this pull request";

        return new AnalysisRequestResponse(
                "ACCEPTED",
                message,
                result.job().getId(),
                request.owner(),
                request.repository(),
                request.pullRequestNumber(),
                Instant.now()
        );
    }
}
