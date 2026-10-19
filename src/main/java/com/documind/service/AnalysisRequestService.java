package com.documind.service;

import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.PullRequestAnalysisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AnalysisRequestService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRequestService.class);

    /**
     * Accepts and validates a manual PR analysis request.
     * Note: In Milestone 1, this acts as a validated entrypoint/stub.
     * Durable job persistence, deduplication, and worker processing will be connected in Milestones 2-4.
     */
    public AnalysisRequestResponse processAnalysisRequest(PullRequestAnalysisRequest request) {
        log.info("Received manual analysis request for {}/{} PR #{}",
                request.owner(), request.repository(), request.pullRequestNumber());

        return new AnalysisRequestResponse(
                "ACCEPTED",
                "Analysis request accepted for processing",
                request.owner(),
                request.repository(),
                request.pullRequestNumber(),
                Instant.now()
        );
    }
}
