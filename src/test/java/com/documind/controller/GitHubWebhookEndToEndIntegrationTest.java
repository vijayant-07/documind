package com.documind.controller;

import com.documind.config.DocuMindProperties;
import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.persistence.AnalysisJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GitHubWebhookEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalysisJobRepository repository;

    @Autowired
    private DocuMindProperties properties;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private String computeHmac(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        return HexFormat.of().formatHex(mac.doFinal(data));
    }

    @Test
    void endToEndWebhookDeliveryShouldPersistJobAndHandleDuplicateIdempotently() throws Exception {
        String secret = properties.getGithub().getWebhookSecret();

        String payload = """
                {
                  "action": "closed",
                  "number": 105,
                  "pull_request": {
                    "number": 105,
                    "title": "Add customer loan validation",
                    "body": "Implements validation rules",
                    "merged": true,
                    "head": {
                      "ref": "feature/loan-validation",
                      "sha": "head-sha-abc"
                    },
                    "base": {
                      "ref": "main",
                      "sha": "base-sha-def"
                    },
                    "merge_commit_sha": "merge-sha-105-final",
                    "user": {
                      "login": "octocat"
                    }
                  },
                  "repository": {
                    "id": 98765,
                    "name": "Loan-Service",
                    "full_name": "octocat/Loan-Service",
                    "default_branch": "main",
                    "owner": {
                      "login": "octocat"
                    }
                  },
                  "sender": {
                    "login": "octocat"
                  }
                }
                """;

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + computeHmac(secret, payloadBytes);

        // 1. First delivery -> 202 Accepted
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-uuid-105")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.idempotencyKey").value("98765:105:merge-sha-105-final"));

        // Verify persisted in database
        Optional<AnalysisJob> jobOpt = repository.findByIdempotencyKey("98765:105:merge-sha-105-final");
        assertTrue(jobOpt.isPresent());
        AnalysisJob job = jobOpt.get();
        assertEquals(AnalysisStatus.RECEIVED, job.getStatus());
        assertEquals("octocat/Loan-Service", job.getRepositoryFullName());
        assertEquals(105, job.getPullRequestNumber());
        assertEquals("main", job.getBaseBranch());
        assertEquals("delivery-uuid-105", job.getGithubDeliveryId());

        // 2. Duplicate delivery (e.g. GitHub retry) -> 200 OK
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", signature)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-uuid-105-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.jobId").value(job.getId()));

        // Verify still exactly 1 job in database
        assertEquals(1, repository.count());

        // 3. Invalid signature delivery -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=invalidhashvalue")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-uuid-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadBytes))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // DB count still 1
        assertEquals(1, repository.count());
    }
}
