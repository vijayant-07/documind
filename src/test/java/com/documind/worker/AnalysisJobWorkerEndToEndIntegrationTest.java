package com.documind.worker;

import com.documind.config.DocuMindProperties;
import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.exception.RetryableJobException;
import com.documind.persistence.AnalysisJobRepository;
import com.documind.service.PullRequestAnalysisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisJobWorkerEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private AnalysisJobWorker worker;

    @Autowired
    private DocuMindProperties properties;

    @MockitoBean
    private PullRequestAnalysisService analysisService;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        reset(analysisService);
    }

    private String computeHmacHex(byte[] body) throws Exception {
        String secret = properties.getGithub().getWebhookSecret();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    @DisplayName("End-to-End: Webhook accepts immediately and worker processes asynchronously outside HTTP request")
    void testWebhookToBackgroundWorkerExecution() throws Exception {
        String payload = """
                {
                  "action": "closed",
                  "pull_request": {
                    "number": 88,
                    "merged": true,
                    "merge_commit_sha": "merge-sha-888",
                    "base": { "ref": "main" },
                    "head": { "ref": "feature/payments", "sha": "head-sha-888" },
                    "body": "Implements payment gateway"
                  },
                  "repository": {
                    "id": 99999,
                    "full_name": "octocat/Payments-Service",
                    "default_branch": "main"
                  }
                }
                """;

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String signature = computeHmacHex(payloadBytes);

        // 1. HTTP request returns 202 Accepted immediately
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.idempotencyKey").value("99999:88:merge-sha-888"));

        // 2. Job exists in DB in initial status (work has NOT been done synchronously in HTTP request)
        AnalysisJob job = repository.findByIdempotencyKey("99999:88:merge-sha-888").orElseThrow();
        assertEquals(AnalysisStatus.RECEIVED, job.getStatus());

        // 3. Background worker claims and executes the job
        int processed = worker.processAvailableJobs();
        assertEquals(1, processed);

        // 4. Job is now COMPLETED
        AnalysisJob completedJob = repository.findById(job.getId()).orElseThrow();
        assertEquals(AnalysisStatus.COMPLETED, completedJob.getStatus());
        assertEquals(1, completedJob.getAttemptCount());
        assertNotNull(completedJob.getStartedAt());
        assertNotNull(completedJob.getCompletedAt());
    }

    @Test
    @DisplayName("End-to-End: Transient failure triggers retryable backoff and recovery")
    void testTransientFailureAndRetrySchedule() {
        AnalysisJob job = new AnalysisJob(
                123L, "octocat/Repo", 10, "main", "head-10", "merge-10",
                "123:10:merge-10", "del-10", AnalysisStatus.RECEIVED
        );
        repository.save(job);

        doThrow(new RetryableJobException("Temporary GitHub 503 error", Duration.ofSeconds(2)))
                .when(analysisService).analyzePullRequest(any());

        int processed = worker.processAvailableJobs();
        assertEquals(1, processed);

        AnalysisJob failedJob = repository.findById(job.getId()).orElseThrow();
        assertEquals(AnalysisStatus.RETRYABLE_FAILURE, failedJob.getStatus());
        assertEquals(1, failedJob.getAttemptCount());
        assertNotNull(failedJob.getNextAttemptAt());
        assertTrue(failedJob.getLastError().contains("Temporary GitHub 503 error"));
    }

    @Test
    @DisplayName("End-to-End: Recovers stuck job after simulated application crash")
    void testStuckJobCrashRecovery() {
        AnalysisJob stuckJob = new AnalysisJob(
                123L, "octocat/Repo", 20, "main", "head-20", "merge-20",
                "123:20:merge-20", "del-20", AnalysisStatus.RECEIVED
        );
        stuckJob = repository.save(stuckJob);

        // Simulate a job left in PROCESSING when application died
        stuckJob.setStatus(AnalysisStatus.PROCESSING);
        stuckJob.setAttemptCount(1);
        stuckJob = repository.save(stuckJob);
        repository.updateLastModified(stuckJob.getId(), Instant.now().minus(30, ChronoUnit.MINUTES));

        // Run recovery scan with 15-minute cutoff
        Instant cutoff = Instant.now().minus(15, ChronoUnit.MINUTES);
        int recovered = worker.recoverStuckJobs(cutoff);
        assertEquals(1, recovered);

        AnalysisJob recoveredJob = repository.findById(stuckJob.getId()).orElseThrow();
        assertEquals(AnalysisStatus.RETRYABLE_FAILURE, recoveredJob.getStatus());
        assertTrue(recoveredJob.getLastError().contains("Recovered interrupted/stuck job"));
        assertNotNull(recoveredJob.getNextAttemptAt());
    }
}
