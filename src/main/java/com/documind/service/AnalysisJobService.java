package com.documind.service;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.persistence.AnalysisJobRepository;
import com.documind.persistence.JobPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AnalysisJobService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);

    private final AnalysisJobRepository repository;
    private final JobPersister jobPersister;

    public AnalysisJobService(AnalysisJobRepository repository, JobPersister jobPersister) {
        this.repository = repository;
        this.jobPersister = jobPersister;
    }

    /**
     * Atomically creates a new job or returns the existing job if one already exists
     * for the given idempotency key. Handles concurrent duplicate submissions safely.
     */
    public JobSubmissionResult createOrGetJob(JobCreateCommand command) {
        Optional<AnalysisJob> existing = repository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Analysis job already exists for idempotency key: {}", command.idempotencyKey());
            return new JobSubmissionResult(existing.get(), false);
        }

        AnalysisJob newJob = new AnalysisJob(
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

        try {
            AnalysisJob saved = jobPersister.saveNew(newJob);
            log.info("Created new analysis job id={} for {} PR #{} with idempotency key {}",
                    saved.getId(), saved.getRepositoryFullName(), saved.getPullRequestNumber(), saved.getIdempotencyKey());
            return new JobSubmissionResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent insert conflict detected for idempotency key: {}. Loading existing job.",
                    command.idempotencyKey());
            AnalysisJob existingAfterConflict = repository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to find existing job after unique constraint conflict on " + command.idempotencyKey(), e));
            return new JobSubmissionResult(existingAfterConflict, false);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisJob> findById(Long jobId) {
        return repository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisJob> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public List<AnalysisJob> findByStatus(AnalysisStatus status) {
        return repository.findByStatus(status);
    }

    @Transactional
    public AnalysisJob transitionStatus(Long jobId, AnalysisStatus targetStatus) {
        AnalysisJob job = getJobOrThrow(jobId);
        job.transitionTo(targetStatus);
        return repository.save(job);
    }

    @Transactional
    public AnalysisJob markProcessing(Long jobId) {
        AnalysisJob job = getJobOrThrow(jobId);
        job.markProcessing();
        return repository.save(job);
    }

    @Transactional
    public AnalysisJob markCompleted(Long jobId, Integer documentationPullRequestNumber, String documentationPullRequestUrl) {
        AnalysisJob job = getJobOrThrow(jobId);
        job.markCompleted(documentationPullRequestNumber, documentationPullRequestUrl);
        return repository.save(job);
    }

    @Transactional
    public AnalysisJob markRetryableFailure(Long jobId, String error, Instant nextAttemptAt) {
        AnalysisJob job = getJobOrThrow(jobId);
        job.markRetryableFailure(error, nextAttemptAt);
        return repository.save(job);
    }

    @Transactional
    public AnalysisJob markFailed(Long jobId, String error) {
        AnalysisJob job = getJobOrThrow(jobId);
        job.markFailed(error);
        return repository.save(job);
    }

    @Transactional
    public AnalysisJob markIgnored(Long jobId, String reason) {
        AnalysisJob job = getJobOrThrow(jobId);
        job.markIgnored(reason);
        return repository.save(job);
    }

    private AnalysisJob getJobOrThrow(Long jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis job not found with id: " + jobId));
    }
}
