package com.documind.worker;

import com.documind.config.DocuMindProperties;
import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.exception.PermanentJobException;
import com.documind.exception.RetryableJobException;
import com.documind.persistence.AnalysisJobRepository;
import com.documind.service.AnalysisJobService;
import com.documind.service.PullRequestAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisJobWorkerTest {

    @Mock
    private AnalysisJobRepository repository;

    @Mock
    private JobClaimService claimService;

    @Mock
    private AnalysisJobService jobService;

    @Mock
    private PullRequestAnalysisService analysisService;

    @Mock
    private RetryBackoffPolicy backoffPolicy;

    private DocuMindProperties properties;
    private AnalysisJobWorker worker;

    @BeforeEach
    void setUp() {
        properties = new DocuMindProperties();
        properties.getWorker().setMaxAttempts(3);
        properties.getWorker().setBatchSize(5);

        worker = new AnalysisJobWorker(
                properties,
                repository,
                claimService,
                jobService,
                analysisService,
                backoffPolicy
        );
    }

    private AnalysisJob createTestJob(Long id, int attemptCount) {
        AnalysisJob job = new AnalysisJob(
                1L, "octocat/Hello-World", 42, "main", "head-sha", "merge-sha",
                "1:42:merge-sha", "delivery-1", AnalysisStatus.PROCESSING
        );
        job.setId(id);
        job.setAttemptCount(attemptCount);
        return job;
    }

    @Test
    @DisplayName("Successfully claims and processes a job to completion")
    void testSuccessfulProcessing() {
        AnalysisJob job = createTestJob(10L, 1);
        when(claimService.claimJob(eq(10L), any(Instant.class))).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(job));

        boolean result = worker.claimAndProcessJob(10L);

        assertTrue(result);
        verify(analysisService).analyzePullRequest(job);
        verify(jobService).markCompleted(eq(10L), any(), any());
        verify(jobService, never()).markFailed(anyLong(), any());
        verify(jobService, never()).markRetryableFailure(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Does not process job if claim was not acquired")
    void testClaimNotAcquired() {
        when(claimService.claimJob(eq(10L), any(Instant.class))).thenReturn(false);

        boolean result = worker.claimAndProcessJob(10L);

        assertFalse(result);
        verify(repository, never()).findById(anyLong());
        verify(analysisService, never()).analyzePullRequest(any());
    }

    @Test
    @DisplayName("Handles retryable failure when attemptCount < maxAttempts")
    void testRetryableFailureWithinMaxAttempts() {
        AnalysisJob job = createTestJob(10L, 1);
        when(claimService.claimJob(eq(10L), any(Instant.class))).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(job));
        doThrow(new RetryableJobException("GitHub API 503 unavailable"))
                .when(analysisService).analyzePullRequest(job);
        when(backoffPolicy.computeDelay(eq(1), any())).thenReturn(Duration.ofSeconds(15));

        boolean result = worker.claimAndProcessJob(10L);

        assertTrue(result);
        verify(jobService).markRetryableFailure(eq(10L), eq("GitHub API 503 unavailable"), any(Instant.class));
        verify(jobService, never()).markCompleted(anyLong(), any(), any());
        verify(jobService, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("Marks job FAILED when retryable failure reaches maxAttempts")
    void testRetryableFailureReachesMaxAttempts() {
        AnalysisJob job = createTestJob(10L, 3); // Max is 3
        when(claimService.claimJob(eq(10L), any(Instant.class))).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(job));
        doThrow(new RetryableJobException("Rate limit timeout"))
                .when(analysisService).analyzePullRequest(job);

        boolean result = worker.claimAndProcessJob(10L);

        assertTrue(result);
        verify(jobService).markFailed(eq(10L), contains("Exceeded maximum retry attempts (3)"));
        verify(jobService, never()).markRetryableFailure(anyLong(), any(), any());
        verify(jobService, never()).markCompleted(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Marks job FAILED immediately on permanent failure without retrying")
    void testPermanentFailure() {
        AnalysisJob job = createTestJob(10L, 1);
        when(claimService.claimJob(eq(10L), any(Instant.class))).thenReturn(true);
        when(repository.findById(10L)).thenReturn(Optional.of(job));
        doThrow(new PermanentJobException("Repository access forbidden 403"))
                .when(analysisService).analyzePullRequest(job);

        boolean result = worker.claimAndProcessJob(10L);

        assertTrue(result);
        verify(jobService).markFailed(eq(10L), eq("Permanent failure: Repository access forbidden 403"));
        verify(jobService, never()).markRetryableFailure(anyLong(), any(), any());
        verify(jobService, never()).markCompleted(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Recovers stuck jobs: retries if attempt < maxAttempts")
    void testRecoverStuckJobRetryable() {
        AnalysisJob stuckJob = createTestJob(20L, 1);
        when(repository.findStuckJobs(any(), any(Instant.class))).thenReturn(List.of(stuckJob));
        when(backoffPolicy.computeDelay(eq(1), any())).thenReturn(Duration.ofSeconds(20));

        int recovered = worker.recoverStuckJobs(Instant.now());

        assertEquals(1, recovered);
        verify(jobService).markRetryableFailure(eq(20L), contains("Recovered interrupted/stuck job"), any(Instant.class));
        verify(jobService, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("Recovers stuck jobs: fails if attempt >= maxAttempts")
    void testRecoverStuckJobMaxAttempts() {
        AnalysisJob stuckJob = createTestJob(20L, 3);
        when(repository.findStuckJobs(any(), any(Instant.class))).thenReturn(List.of(stuckJob));

        int recovered = worker.recoverStuckJobs(Instant.now());

        assertEquals(1, recovered);
        verify(jobService).markFailed(eq(20L), contains("Job timed out and exceeded max attempts (3)"));
        verify(jobService, never()).markRetryableFailure(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Worker skips polling when disabled in configuration")
    void testWorkerDisabledSkipsPolling() {
        properties.getWorker().setEnabled(false);

        worker.scheduledPollAndProcess();

        verify(repository, never()).findClaimableJobIds(any(), any(), any(), any());
    }
}

