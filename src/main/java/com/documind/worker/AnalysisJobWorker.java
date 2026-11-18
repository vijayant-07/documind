package com.documind.worker;

import com.documind.config.DocuMindProperties;
import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.exception.PermanentJobException;
import com.documind.exception.RetryableJobException;
import com.documind.persistence.AnalysisJobRepository;
import com.documind.service.AnalysisJobService;
import com.documind.service.PullRequestAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background worker responsible for polling, claiming, processing,
 * retrying, and recovering analysis jobs outside the HTTP request lifecycle.
 */
@Component
public class AnalysisJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobWorker.class);

    private final DocuMindProperties properties;
    private final AnalysisJobRepository repository;
    private final JobClaimService claimService;
    private final AnalysisJobService jobService;
    private final PullRequestAnalysisService analysisService;
    private final RetryBackoffPolicy backoffPolicy;

    private final AtomicBoolean pollingInProgress = new AtomicBoolean(false);

    public AnalysisJobWorker(
            DocuMindProperties properties,
            AnalysisJobRepository repository,
            JobClaimService claimService,
            AnalysisJobService jobService,
            PullRequestAnalysisService analysisService,
            RetryBackoffPolicy backoffPolicy
    ) {
        this.properties = properties;
        this.repository = repository;
        this.claimService = claimService;
        this.jobService = jobService;
        this.analysisService = analysisService;
        this.backoffPolicy = backoffPolicy;
    }

    /**
     * Periodic scheduled poller for claiming and processing available jobs.
     */
    @Scheduled(fixedDelayString = "${documind.worker.poll-interval-ms:5000}")
    public void scheduledPollAndProcess() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }

        if (!pollingInProgress.compareAndSet(false, true)) {
            log.debug("Skipping poll execution: previous polling cycle is still active");
            return;
        }

        try {
            int processed = processAvailableJobs();
            if (processed > 0) {
                log.info("Worker polling cycle completed: processed {} job(s)", processed);
            }
        } catch (Exception e) {
            log.error("Unexpected error during worker polling cycle: {}", e.getMessage(), e);
        } finally {
            pollingInProgress.set(false);
        }
    }

    /**
     * Claims and processes available jobs up to the configured batch size.
     *
     * @return Number of jobs claimed and processed in this run
     */
    public int processAvailableJobs() {
        Instant now = Instant.now();
        int batchSize = properties.getWorker().getBatchSize();

        List<Long> claimableJobIds = repository.findClaimableJobIds(
                List.of(AnalysisStatus.RECEIVED, AnalysisStatus.QUEUED),
                AnalysisStatus.RETRYABLE_FAILURE,
                now,
                PageRequest.of(0, batchSize)
        );

        if (claimableJobIds.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (Long jobId : claimableJobIds) {
            if (claimAndProcessJob(jobId)) {
                processedCount++;
            }
        }

        return processedCount;
    }

    /**
     * Atomically claims a single job and executes its processing pipeline.
     *
     * @param jobId The ID of the job to process
     * @return true if claimed and handled; false if claim was not acquired
     */
    public boolean claimAndProcessJob(Long jobId) {
        Instant now = Instant.now();
        boolean claimed = claimService.claimJob(jobId, now);
        if (!claimed) {
            log.debug("Job id={} was not claimed (already claimed or ineligible)", jobId);
            return false;
        }

        AnalysisJob job = repository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job id={} was claimed but could not be reloaded", jobId);
            return false;
        }

        int maxAttempts = properties.getWorker().getMaxAttempts();
        log.info("Claimed analysis job id={} for {} PR #{} (attempt {}/{})",
                job.getId(), job.getRepositoryFullName(), job.getPullRequestNumber(),
                job.getAttemptCount(), maxAttempts);

        Instant start = Instant.now();
        try {
            analysisService.analyzePullRequest(job);

            jobService.markCompleted(
                    job.getId(),
                    job.getDocumentationPullRequestNumber(),
                    job.getDocumentationPullRequestUrl()
            );

            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            log.info("Successfully completed analysis job id={} for {} PR #{} in {}ms",
                    job.getId(), job.getRepositoryFullName(), job.getPullRequestNumber(), elapsedMs);
            return true;

        } catch (RetryableJobException e) {
            handleRetryableFailure(job, e);
            return true;

        } catch (PermanentJobException e) {
            handlePermanentFailure(job, e);
            return true;

        } catch (Exception e) {
            handleUnexpectedFailure(job, e);
            return true;
        }
    }

    private void handleRetryableFailure(AnalysisJob job, RetryableJobException e) {
        int maxAttempts = properties.getWorker().getMaxAttempts();
        if (job.getAttemptCount() >= maxAttempts) {
            String errorMsg = "Exceeded maximum retry attempts (" + maxAttempts + "). Last error: " + e.getMessage();
            log.error("Job id={} permanently failed after {} attempts: {}", job.getId(), job.getAttemptCount(), errorMsg);
            jobService.markFailed(job.getId(), errorMsg);
        } else {
            Duration delay = backoffPolicy.computeDelay(job.getAttemptCount(), e.getRetryAfter());
            Instant nextAttemptAt = Instant.now().plus(delay);
            log.warn("Job id={} failed retryably (attempt {}/{}). Retrying at {} (in {}s). Error: {}",
                    job.getId(), job.getAttemptCount(), maxAttempts, nextAttemptAt, delay.toSeconds(), e.getMessage());
            jobService.markRetryableFailure(job.getId(), e.getMessage(), nextAttemptAt);
        }
    }

    private void handlePermanentFailure(AnalysisJob job, PermanentJobException e) {
        String errorMsg = "Permanent failure: " + e.getMessage();
        log.error("Job id={} permanently failed on attempt {}: {}", job.getId(), job.getAttemptCount(), errorMsg);
        jobService.markFailed(job.getId(), errorMsg);
    }

    private void handleUnexpectedFailure(AnalysisJob job, Exception e) {
        int maxAttempts = properties.getWorker().getMaxAttempts();
        if (job.getAttemptCount() >= maxAttempts) {
            String errorMsg = "Exceeded maximum attempts (" + maxAttempts + ") after unexpected error: " + e.getMessage();
            log.error("Job id={} permanently failed: {}", job.getId(), errorMsg, e);
            jobService.markFailed(job.getId(), errorMsg);
        } else {
            Duration delay = backoffPolicy.computeDelay(job.getAttemptCount(), null);
            Instant nextAttemptAt = Instant.now().plus(delay);
            log.warn("Job id={} encountered unexpected error (attempt {}/{}). Retrying at {} (in {}s): {}",
                    job.getId(), job.getAttemptCount(), maxAttempts, nextAttemptAt, delay.toSeconds(), e.getMessage(), e);
            jobService.markRetryableFailure(job.getId(), e.getMessage(), nextAttemptAt);
        }
    }

    /**
     * Periodic and startup scan to detect and recover jobs left in an active state
     * (PROCESSING, GENERATING_DOCUMENTATION, CREATING_PULL_REQUEST) beyond the timeout window,
     * such as following an application crash, hard restart, or worker node death.
     *
     * @param cutoff Timestamp before which active jobs are considered stuck
     * @return Number of stuck jobs recovered
     */
    public int recoverStuckJobs(Instant cutoff) {
        List<AnalysisJob> stuckJobs = repository.findStuckJobs(
                List.of(
                        AnalysisStatus.PROCESSING,
                        AnalysisStatus.GENERATING_DOCUMENTATION,
                        AnalysisStatus.CREATING_PULL_REQUEST
                ),
                cutoff
        );

        if (stuckJobs.isEmpty()) {
            return 0;
        }

        log.warn("Discovered {} stuck job(s) with last update before {}", stuckJobs.size(), cutoff);
        int maxAttempts = properties.getWorker().getMaxAttempts();
        int recovered = 0;

        for (AnalysisJob job : stuckJobs) {
            if (job.getAttemptCount() >= maxAttempts) {
                String errorMsg = "Job timed out and exceeded max attempts (" + maxAttempts + ") while in status " + job.getStatus();
                log.error("Failing stuck job id={} after {} attempts", job.getId(), job.getAttemptCount());
                jobService.markFailed(job.getId(), errorMsg);
            } else {
                Duration delay = backoffPolicy.computeDelay(job.getAttemptCount(), null);
                Instant nextAttemptAt = Instant.now().plus(delay);
                String reason = "Recovered interrupted/stuck job from state " + job.getStatus();
                log.warn("Recovering stuck job id={} from state {}. Rescheduled for retry at {}", job.getId(), job.getStatus(), nextAttemptAt);
                jobService.markRetryableFailure(job.getId(), reason, nextAttemptAt);
            }
            recovered++;
        }

        return recovered;
    }

    /**
     * Periodic scheduled task to recover stuck jobs.
     */
    @Scheduled(fixedDelayString = "${documind.worker.recovery-interval-ms:60000}")
    public void scheduledRecovery() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }

        try {
            int timeoutMinutes = properties.getWorker().getStuckJobTimeoutMinutes();
            Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
            int recovered = recoverStuckJobs(cutoff);
            if (recovered > 0) {
                log.info("Recovery maintenance completed: recovered {} stuck job(s)", recovered);
            }
        } catch (Exception e) {
            log.error("Error during scheduled stuck job recovery: {}", e.getMessage(), e);
        }
    }

    /**
     * On application startup, scan for jobs interrupted by previous process shutdown/crash.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (properties.getWorker().isEnabled()) {
            log.info("Worker startup: running initial stuck job recovery scan...");
            int timeoutMinutes = properties.getWorker().getStuckJobTimeoutMinutes();
            Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
            recoverStuckJobs(cutoff);
        }
    }
}

