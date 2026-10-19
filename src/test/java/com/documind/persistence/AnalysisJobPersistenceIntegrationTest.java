package com.documind.persistence;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.service.AnalysisJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AnalysisJobPersistenceIntegrationTest {

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private AnalysisJobService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void jobShouldPersistAndBeRetrievableByIdempotencyKey() {
        JobCreateCommand command = new JobCreateCommand(
                123456L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha-1",
                "merge-sha-1",
                JobCreateCommand.buildWebhookIdempotencyKey(123456L, 42, "merge-sha-1"),
                "delivery-uuid-1"
        );

        JobSubmissionResult result = service.createOrGetJob(command);

        assertTrue(result.newlyCreated());
        assertNotNull(result.job().getId());

        Optional<AnalysisJob> retrieved = repository.findByIdempotencyKey(command.idempotencyKey());
        assertTrue(retrieved.isPresent());
        assertEquals("octocat/Hello-World", retrieved.get().getRepositoryFullName());
        assertEquals(42, retrieved.get().getPullRequestNumber());
        assertEquals(AnalysisStatus.RECEIVED, retrieved.get().getStatus());
        assertEquals("delivery-uuid-1", retrieved.get().getGithubDeliveryId());
    }

    @Test
    void repeatedSubmissionsShouldBeIdempotentAndNotCreateDuplicateJobs() {
        String idempotencyKey = JobCreateCommand.buildWebhookIdempotencyKey(123456L, 42, "merge-sha-repeat");
        JobCreateCommand command = new JobCreateCommand(
                123456L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha-1",
                "merge-sha-repeat",
                idempotencyKey,
                "delivery-uuid-repeat"
        );

        // First delivery
        JobSubmissionResult firstResult = service.createOrGetJob(command);
        assertTrue(firstResult.newlyCreated());
        Long initialJobId = firstResult.job().getId();

        // Second delivery (e.g. GitHub webhook retry)
        JobSubmissionResult secondResult = service.createOrGetJob(command);
        assertFalse(secondResult.newlyCreated());
        assertEquals(initialJobId, secondResult.job().getId());

        // Total count in DB must remain 1
        assertEquals(1, repository.count());
    }

    @Test
    void directDuplicateIdempotencyKeyInsertShouldFailWithUniqueConstraintViolation() {
        String idempotencyKey = "unique-key-test";

        AnalysisJob job1 = new AnalysisJob(
                1L, "octocat/Hello-World", 1, "main", "h1", "m1", idempotencyKey, "d1", AnalysisStatus.RECEIVED
        );
        repository.saveAndFlush(job1);

        AnalysisJob job2 = new AnalysisJob(
                1L, "octocat/Hello-World", 1, "main", "h2", "m2", idempotencyKey, "d2", AnalysisStatus.RECEIVED
        );

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(job2));
    }

    @Test
    void concurrentDuplicateSubmissionsShouldAllSucceedWithOnlyOneJobCreated() throws InterruptedException {
        int threadCount = 8;
        String idempotencyKey = JobCreateCommand.buildWebhookIdempotencyKey(9999L, 100, "merge-concurrent");
        JobCreateCommand command = new JobCreateCommand(
                9999L,
                "octocat/Concurrent-Repo",
                100,
                "main",
                "head-c",
                "merge-concurrent",
                idempotencyKey,
                "delivery-c"
        );

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<JobSubmissionResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    JobSubmissionResult res = service.createOrGetJob(command);
                    results.add(res);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all threads at once
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "Expected no unhandled exceptions in concurrent submissions: " + errors);
        assertEquals(threadCount, results.size());

        // Exactly one submission should report newlyCreated == true
        long newlyCreatedCount = results.stream().filter(JobSubmissionResult::newlyCreated).count();
        assertEquals(1, newlyCreatedCount);

        // All results must reference the exact same job ID
        Long firstId = results.getFirst().job().getId();
        assertTrue(results.stream().allMatch(r -> r.job().getId().equals(firstId)));

        // In database, count must be exactly 1
        assertEquals(1, repository.count());
    }

    @Test
    void statusTransitionsShouldPersistCorrectly() {
        JobCreateCommand command = new JobCreateCommand(
                123L, "octocat/Repo", 5, "main", "h", "m", "status-test-key", "d"
        );
        JobSubmissionResult result = service.createOrGetJob(command);
        Long jobId = result.job().getId();

        // Mark processing
        AnalysisJob processingJob = service.markProcessing(jobId);
        assertEquals(AnalysisStatus.PROCESSING, processingJob.getStatus());
        assertEquals(1, processingJob.getAttemptCount());
        assertNotNull(processingJob.getStartedAt());

        // Mark completed
        AnalysisJob completedJob = service.markCompleted(jobId, 12, "https://github.com/octocat/Repo/pull/12");
        assertEquals(AnalysisStatus.COMPLETED, completedJob.getStatus());
        assertEquals(12, completedJob.getDocumentationPullRequestNumber());
        assertEquals("https://github.com/octocat/Repo/pull/12", completedJob.getDocumentationPullRequestUrl());
        assertNotNull(completedJob.getCompletedAt());

        // Reload from repository and verify persisted state
        AnalysisJob reloaded = repository.findById(jobId).orElseThrow();
        assertEquals(AnalysisStatus.COMPLETED, reloaded.getStatus());
        assertEquals(12, reloaded.getDocumentationPullRequestNumber());
        assertNotNull(reloaded.getCompletedAt());
        assertNotNull(reloaded.getStartedAt());
    }
}
