package com.documind.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisJobTest {

    private AnalysisJob job;

    @BeforeEach
    void setUp() {
        job = new AnalysisJob(
                12345L,
                "octocat/Hello-World",
                42,
                "main",
                "head123",
                "merge456",
                "12345:42:merge456",
                "delivery-789",
                AnalysisStatus.RECEIVED
        );
    }

    @Test
    void defaultValuesShouldBeProperlyInitialized() {
        assertEquals(AnalysisStatus.RECEIVED, job.getStatus());
        assertEquals(0, job.getAttemptCount());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
        assertNull(job.getStartedAt());
        assertNull(job.getCompletedAt());
        assertNull(job.getLastError());
    }

    @Test
    void markProcessingShouldIncrementAttemptAndSetStartedAt() {
        job.markProcessing();

        assertEquals(AnalysisStatus.PROCESSING, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertNotNull(job.getStartedAt());
    }

    @Test
    void markCompletedShouldSetStatusAndCompletedAt() {
        job.markProcessing();
        job.transitionTo(AnalysisStatus.GENERATING_DOCUMENTATION);
        job.transitionTo(AnalysisStatus.CREATING_PULL_REQUEST);
        job.markCompleted(100, "https://github.com/octocat/Hello-World/pull/100");

        assertEquals(AnalysisStatus.COMPLETED, job.getStatus());
        assertEquals(100, job.getDocumentationPullRequestNumber());
        assertEquals("https://github.com/octocat/Hello-World/pull/100", job.getDocumentationPullRequestUrl());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void markRetryableFailureShouldSetErrorAndNextAttemptAt() {
        job.markProcessing();
        Instant retryAt = Instant.now().plusSeconds(60);
        job.markRetryableFailure("Transient network failure", retryAt);

        assertEquals(AnalysisStatus.RETRYABLE_FAILURE, job.getStatus());
        assertEquals("Transient network failure", job.getLastError());
        assertEquals(retryAt, job.getNextAttemptAt());
    }

    @Test
    void markFailedShouldSetStatusAndCompletedAt() {
        job.markProcessing();
        job.markFailed("Fatal authentication error");

        assertEquals(AnalysisStatus.FAILED, job.getStatus());
        assertEquals("Fatal authentication error", job.getLastError());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void markIgnoredShouldSetStatusAndCompletedAt() {
        job.markIgnored("Base branch is not configured");

        assertEquals(AnalysisStatus.IGNORED, job.getStatus());
        assertEquals("Base branch is not configured", job.getLastError());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    void sanitizeErrorShouldTruncateOversizedMessages() {
        job.markProcessing();
        String veryLongError = "E".repeat(3000);
        job.markFailed(veryLongError);

        assertEquals(2000, job.getLastError().length());
        assertTrue(job.getLastError().endsWith("..."));
    }

    @Test
    void illegalStatusTransitionShouldThrowException() {
        job.markProcessing();
        job.transitionTo(AnalysisStatus.GENERATING_DOCUMENTATION);
        job.transitionTo(AnalysisStatus.CREATING_PULL_REQUEST);
        job.markCompleted(100, "https://github.com/octocat/Hello-World/pull/100");

        // Cannot transition from COMPLETED to QUEUED
        assertThrows(IllegalStateException.class, () -> job.transitionTo(AnalysisStatus.QUEUED));
    }
}
