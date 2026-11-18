package com.documind.worker;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.persistence.AnalysisJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AnalysisJobWorkerConcurrencyIntegrationTest {

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private JobClaimService claimService;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Concurrent workers competing for the same job: exactly one claims it")
    void testConcurrentWorkersClaimSameJob() throws InterruptedException, ExecutionException {
        // Given a job in RECEIVED status
        AnalysisJob job = new AnalysisJob(
                100L,
                "octocat/Hello-World",
                55,
                "main",
                "head-sha-55",
                "merge-sha-55",
                "100:55:merge-sha-55",
                "del-55",
                AnalysisStatus.RECEIVED
        );
        AnalysisJob saved = repository.save(job);
        Long jobId = saved.getId();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successClaims = new AtomicInteger(0);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                startGate.await(); // ensure all threads fire at once
                boolean claimed = claimService.claimJob(jobId, Instant.now());
                if (claimed) {
                    successClaims.incrementAndGet();
                }
                return claimed;
            });
        }

        startGate.countDown();
        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Exactly 1 worker succeeded in claiming the job
        assertEquals(1, successClaims.get());

        // In database: Job is PROCESSING with attemptCount = 1
        AnalysisJob updated = repository.findById(jobId).orElseThrow();
        assertEquals(AnalysisStatus.PROCESSING, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertTrue(updated.getStartedAt() != null);
    }
}

