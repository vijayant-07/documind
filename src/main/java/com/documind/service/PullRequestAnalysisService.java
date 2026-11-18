package com.documind.service;

import com.documind.domain.AnalysisJob;
import com.documind.exception.JobExecutionException;

/**
 * Service interface for analyzing a pull request and generating documentation changes.
 * This coordinates the end-to-end pipeline: PR retrieval, diff analysis, LLM generation,
 * changelog updates, and documentation pull request opening.
 */
public interface PullRequestAnalysisService {

    /**
     * Executes PR analysis and documentation generation for the given job.
     *
     * @param job The active analysis job currently in PROCESSING status
     * @throws JobExecutionException on failure (RetryableJobException or PermanentJobException)
     */
    void analyzePullRequest(AnalysisJob job) throws JobExecutionException;
}

