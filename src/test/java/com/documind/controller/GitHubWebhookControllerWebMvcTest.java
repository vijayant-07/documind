package com.documind.controller;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.dto.GitHubWebhookPayload;
import com.documind.dto.WebhookProcessingResult;
import com.documind.service.WebhookEventService;
import com.documind.service.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubWebhookController.class)
@Import(GlobalExceptionHandler.class)
class GitHubWebhookControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSignatureVerifier signatureVerifier;

    @MockitoBean
    private WebhookEventService webhookEventService;

    private static final String VALID_PAYLOAD = """
            {
              "action": "closed",
              "number": 42,
              "pull_request": {
                "number": 42,
                "title": "Add customer risk scoring",
                "body": "PR description",
                "merged": true,
                "head": {
                  "ref": "feature/risk-scoring",
                  "sha": "head-sha-123"
                },
                "base": {
                  "ref": "main",
                  "sha": "base-sha-456"
                },
                "merge_commit_sha": "merge-sha-789",
                "user": {
                  "login": "octocat"
                }
              },
              "repository": {
                "id": 123456,
                "name": "Hello-World",
                "full_name": "octocat/Hello-World",
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

    @Test
    void webhookWithValidSignatureAndQualifyingEventShouldReturn202Accepted() throws Exception {
        when(signatureVerifier.verifySignature(eq("sha256=validsig"), any(byte[].class))).thenReturn(true);

        AnalysisJob job = new AnalysisJob(
                123456L, "octocat/Hello-World", 42, "main", "head-sha-123", "merge-sha-789",
                "123456:42:merge-sha-789", "delivery-uuid", AnalysisStatus.RECEIVED
        );
        job.setId(88L);

        when(webhookEventService.processWebhookEvent(eq("pull_request"), eq("delivery-uuid"), any(GitHubWebhookPayload.class)))
                .thenReturn(WebhookProcessingResult.accepted(job));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=validsig")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.jobId").value(88))
                .andExpect(jsonPath("$.idempotencyKey").value("123456:42:merge-sha-789"));

        verify(webhookEventService).processWebhookEvent(eq("pull_request"), eq("delivery-uuid"), any(GitHubWebhookPayload.class));
    }

    @Test
    void webhookWithDuplicateDeliveryShouldReturn200Ok() throws Exception {
        when(signatureVerifier.verifySignature(eq("sha256=validsig"), any(byte[].class))).thenReturn(true);

        AnalysisJob job = new AnalysisJob(
                123456L, "octocat/Hello-World", 42, "main", "head-sha-123", "merge-sha-789",
                "123456:42:merge-sha-789", "delivery-uuid", AnalysisStatus.RECEIVED
        );
        job.setId(88L);

        when(webhookEventService.processWebhookEvent(eq("pull_request"), eq("delivery-uuid"), any(GitHubWebhookPayload.class)))
                .thenReturn(WebhookProcessingResult.duplicate(job));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=validsig")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.jobId").value(88))
                .andExpect(jsonPath("$.message").value("Duplicate webhook delivery; job already exists"));
    }

    @Test
    void webhookWithIgnoredEventShouldReturn200OkWithReason() throws Exception {
        when(signatureVerifier.verifySignature(eq("sha256=validsig"), any(byte[].class))).thenReturn(true);

        when(webhookEventService.processWebhookEvent(eq("pull_request"), eq("delivery-uuid"), any(GitHubWebhookPayload.class)))
                .thenReturn(WebhookProcessingResult.ignored("Pull request #42 was closed unmerged"));

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=validsig")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"))
                .andExpect(jsonPath("$.message").value("Pull request #42 was closed unmerged"));
    }

    @Test
    void webhookWithInvalidSignatureShouldReturn401Unauthorized() throws Exception {
        when(signatureVerifier.verifySignature(anyString(), any(byte[].class))).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=invalidsig")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or missing webhook signature"));

        verify(webhookEventService, never()).processWebhookEvent(any(), any(), any());
    }

    @Test
    void webhookWithMissingSignatureHeaderShouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or missing webhook signature"));

        verify(webhookEventService, never()).processWebhookEvent(any(), any(), any());
    }

    @Test
    void webhookWithMissingEventHeaderShouldReturn400BadRequest() throws Exception {
        when(signatureVerifier.verifySignature(eq("sha256=validsig"), any(byte[].class))).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=validsig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing required X-GitHub-Event header"));

        verify(webhookEventService, never()).processWebhookEvent(any(), any(), any());
    }

    @Test
    void webhookWithEmptyBodyShouldReturn400BadRequest() throws Exception {
        when(signatureVerifier.verifySignature(eq("sha256=validsig"), any(byte[].class))).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=validsig")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(webhookEventService, never()).processWebhookEvent(any(), any(), any());
    }

    @Test
    void webhookWithMalformedJsonPayloadShouldReturn400BadRequest() throws Exception {
        when(signatureVerifier.verifySignature(eq("sha256=validsig"), any(byte[].class))).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-Hub-Signature-256", "sha256=validsig")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{action: invalid_json, number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON webhook payload"));

        verify(webhookEventService, never()).processWebhookEvent(any(), any(), any());
    }
}
