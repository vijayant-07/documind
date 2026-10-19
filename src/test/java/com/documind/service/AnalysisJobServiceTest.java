package com.documind.service;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.persistence.AnalysisJobRepository;
import com.documind.persistence.JobPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisJobServiceTest {

    private AnalysisJobRepository repository;
    private JobPersister jobPersister;
    private AnalysisJobService service;

    @BeforeEach
    void setUp() {
        repository = mock(AnalysisJobRepository.class);
        jobPersister = mock(JobPersister.class);
        service = new AnalysisJobService(repository, jobPersister);
    }

    @Test
    void createOrGetJobShouldCreateNewJobWhenNotExists() {
        JobCreateCommand command = new JobCreateCommand(
                12345L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha",
                "merge-sha",
                "12345:42:merge-sha",
                "delivery-1"
        );

        when(repository.findByIdempotencyKey(command.idempotencyKey())).thenReturn(Optional.empty());

        AnalysisJob savedJob = new AnalysisJob(
                command.repositoryId(),
                command.repositoryFullName(),
                command.pullRequestNumber(),
                command.baseBranch(),
                command.headSha(),
                command.mergeSha(),
                command.idempotencyKey(),
                command.githubDeliveryId(),
                AnalysisStatus.RECEIVED
        );
        savedJob.setId(1L);

        when(jobPersister.saveNew(any(AnalysisJob.class))).thenReturn(savedJob);

        JobSubmissionResult result = service.createOrGetJob(command);

        assertTrue(result.newlyCreated());
        assertEquals(1L, result.job().getId());
        assertEquals("12345:42:merge-sha", result.job().getIdempotencyKey());
        verify(jobPersister).saveNew(any(AnalysisJob.class));
    }

    @Test
    void createOrGetJobShouldReturnExistingJobWhenAlreadyPresent() {
        JobCreateCommand command = new JobCreateCommand(
                12345L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha",
                "merge-sha",
                "12345:42:merge-sha",
                "delivery-1"
        );

        AnalysisJob existingJob = new AnalysisJob(
                command.repositoryId(),
                command.repositoryFullName(),
                command.pullRequestNumber(),
                command.baseBranch(),
                command.headSha(),
                command.mergeSha(),
                command.idempotencyKey(),
                command.githubDeliveryId(),
                AnalysisStatus.RECEIVED
        );
        existingJob.setId(99L);

        when(repository.findByIdempotencyKey(command.idempotencyKey())).thenReturn(Optional.of(existingJob));

        JobSubmissionResult result = service.createOrGetJob(command);

        assertFalse(result.newlyCreated());
        assertEquals(99L, result.job().getId());
        verify(jobPersister, never()).saveNew(any(AnalysisJob.class));
    }

    @Test
    void createOrGetJobShouldHandleConcurrentConflictSafely() {
        JobCreateCommand command = new JobCreateCommand(
                12345L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha",
                "merge-sha",
                "12345:42:merge-sha",
                "delivery-1"
        );

        AnalysisJob existingJob = new AnalysisJob(
                command.repositoryId(),
                command.repositoryFullName(),
                command.pullRequestNumber(),
                command.baseBranch(),
                command.headSha(),
                command.mergeSha(),
                command.idempotencyKey(),
                command.githubDeliveryId(),
                AnalysisStatus.RECEIVED
        );
        existingJob.setId(99L);

        // Initially not found by read
        when(repository.findByIdempotencyKey(command.idempotencyKey()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingJob));

        // Concurrent insert causes DataIntegrityViolationException
        when(jobPersister.saveNew(any(AnalysisJob.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        JobSubmissionResult result = service.createOrGetJob(command);

        assertFalse(result.newlyCreated());
        assertEquals(99L, result.job().getId());
    }

    @Test
    void transitionStatusShouldUpdateStatusWhenValid() {
        AnalysisJob job = new AnalysisJob(
                1L, "octocat/Hello-World", 42, "main", "h", "m", "k", "d", AnalysisStatus.RECEIVED
        );
        job.setId(10L);

        when(repository.findById(10L)).thenReturn(Optional.of(job));
        when(repository.save(job)).thenReturn(job);

        AnalysisJob updated = service.transitionStatus(10L, AnalysisStatus.QUEUED);

        assertEquals(AnalysisStatus.QUEUED, updated.getStatus());
        verify(repository).save(job);
    }

    @Test
    void markProcessingShouldIncrementAttempt() {
        AnalysisJob job = new AnalysisJob(
                1L, "octocat/Hello-World", 42, "main", "h", "m", "k", "d", AnalysisStatus.RECEIVED
        );
        job.setId(10L);

        when(repository.findById(10L)).thenReturn(Optional.of(job));
        when(repository.save(job)).thenReturn(job);

        AnalysisJob updated = service.markProcessing(10L);

        assertEquals(AnalysisStatus.PROCESSING, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertNotNull(updated.getStartedAt());
    }

    @Test
    void markCompletedShouldSetDocPrInfo() {
        AnalysisJob job = new AnalysisJob(
                1L, "octocat/Hello-World", 42, "main", "h", "m", "k", "d", AnalysisStatus.CREATING_PULL_REQUEST
        );
        job.setId(10L);

        when(repository.findById(10L)).thenReturn(Optional.of(job));
        when(repository.save(job)).thenReturn(job);

        AnalysisJob updated = service.markCompleted(10L, 55, "https://github.com/pr/55");

        assertEquals(AnalysisStatus.COMPLETED, updated.getStatus());
        assertEquals(55, updated.getDocumentationPullRequestNumber());
        assertEquals("https://github.com/pr/55", updated.getDocumentationPullRequestUrl());
        assertNotNull(updated.getCompletedAt());
    }

    @Test
    void transitionStatusShouldThrowWhenJobNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.transitionStatus(999L, AnalysisStatus.QUEUED));
    }
}
