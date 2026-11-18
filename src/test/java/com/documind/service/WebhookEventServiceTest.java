package com.documind.service;

import com.documind.config.DocuMindProperties;
import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.dto.BranchDetails;
import com.documind.dto.GitHubWebhookPayload;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.dto.PullRequestDetails;
import com.documind.dto.RepositoryDetails;
import com.documind.dto.UserDetails;
import com.documind.dto.WebhookProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookEventServiceTest {

    private DocuMindProperties properties;
    private AnalysisJobService analysisJobService;
    private WebhookEventService service;

    @BeforeEach
    void setUp() {
        properties = new DocuMindProperties();
        properties.getGithub().setAllowedBranches(List.of("main", "master"));
        properties.getGithub().setBranchPrefix("documind/docs-pr-");
        properties.getGithub().setPrBodyMarker("<!-- documind-generated -->");

        analysisJobService = mock(AnalysisJobService.class);
        service = new WebhookEventService(properties, analysisJobService);
    }

    private GitHubWebhookPayload createPayload(
            String action,
            boolean merged,
            String headRef,
            String baseRef,
            String body
    ) {
        BranchDetails head = new BranchDetails(headRef, "head-sha-123");
        BranchDetails base = new BranchDetails(baseRef, "base-sha-456");
        UserDetails user = new UserDetails("octocat");
        PullRequestDetails pr = new PullRequestDetails(
                42,
                "Add customer risk scoring",
                body,
                merged,
                head,
                base,
                "merge-sha-789",
                user
        );
        RepositoryDetails repo = new RepositoryDetails(
                123456L,
                "Hello-World",
                "octocat/Hello-World",
                "main",
                user
        );
        return new GitHubWebhookPayload(action, 42, pr, repo, user);
    }

    @Test
    void processWebhookEventShouldIgnoreNonPullRequestEvents() {
        GitHubWebhookPayload payload = createPayload("closed", true, "feature/scoring", "main", "Normal description");

        WebhookProcessingResult result = service.processWebhookEvent("push", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.IGNORED, result.type());
        assertEquals("Event type 'push' is not supported", result.message());
        verify(analysisJobService, never()).createOrGetJob(any());
    }

    @Test
    void processWebhookEventShouldIgnoreNonClosedActions() {
        GitHubWebhookPayload payload = createPayload("opened", false, "feature/scoring", "main", "Normal description");

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.IGNORED, result.type());
        assertEquals("Pull request action 'opened' is not 'closed'", result.message());
        verify(analysisJobService, never()).createOrGetJob(any());
    }

    @Test
    void processWebhookEventShouldIgnoreUnmergedClosedPRs() {
        GitHubWebhookPayload payload = createPayload("closed", false, "feature/scoring", "main", "Normal description");

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.IGNORED, result.type());
        assertEquals("Pull request #42 was closed unmerged", result.message());
        verify(analysisJobService, never()).createOrGetJob(any());
    }

    @Test
    void processWebhookEventShouldIgnoreDocuMindBranchPrefix() {
        GitHubWebhookPayload payload = createPayload(
                "closed", true, "documind/docs-pr-42-abc", "main", "Normal description"
        );

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.IGNORED, result.type());
        assertEquals("Ignoring DocuMind-generated pull request branch: documind/docs-pr-42-abc", result.message());
        verify(analysisJobService, never()).createOrGetJob(any());
    }

    @Test
    void processWebhookEventShouldIgnoreDocuMindBodyMarker() {
        GitHubWebhookPayload payload = createPayload(
                "closed", true, "feature/doc-fix", "main", "Automated docs update <!-- documind-generated -->"
        );

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.IGNORED, result.type());
        assertEquals("Ignoring DocuMind-generated pull request with marker: <!-- documind-generated -->", result.message());
        verify(analysisJobService, never()).createOrGetJob(any());
    }

    @Test
    void processWebhookEventShouldIgnoreUnallowedBaseBranch() {
        GitHubWebhookPayload payload = createPayload(
                "closed", true, "feature/scoring", "dev", "Normal description"
        );

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.IGNORED, result.type());
        assertEquals("Base branch 'dev' is not configured for documentation generation", result.message());
        verify(analysisJobService, never()).createOrGetJob(any());
    }

    @Test
    void processWebhookEventShouldAcceptQualifyingMergedPR() {
        GitHubWebhookPayload payload = createPayload(
                "closed", true, "feature/scoring", "main", "Feature description"
        );

        AnalysisJob mockJob = new AnalysisJob(
                123456L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha-123",
                "merge-sha-789",
                "123456:42:merge-sha-789",
                "delivery-1",
                AnalysisStatus.RECEIVED
        );
        mockJob.setId(777L);

        when(analysisJobService.createOrGetJob(any(JobCreateCommand.class)))
                .thenReturn(new JobSubmissionResult(mockJob, true));

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.ACCEPTED, result.type());
        assertNotNull(result.job());
        assertEquals(777L, result.job().getId());
        assertEquals("Qualifying pull request merge event accepted for documentation analysis", result.message());
        verify(analysisJobService).createOrGetJob(any(JobCreateCommand.class));
    }

    @Test
    void processWebhookEventShouldRecognizeDuplicateDelivery() {
        GitHubWebhookPayload payload = createPayload(
                "closed", true, "feature/scoring", "main", "Feature description"
        );

        AnalysisJob mockJob = new AnalysisJob(
                123456L,
                "octocat/Hello-World",
                42,
                "main",
                "head-sha-123",
                "merge-sha-789",
                "123456:42:merge-sha-789",
                "delivery-1",
                AnalysisStatus.RECEIVED
        );
        mockJob.setId(777L);

        when(analysisJobService.createOrGetJob(any(JobCreateCommand.class)))
                .thenReturn(new JobSubmissionResult(mockJob, false));

        WebhookProcessingResult result = service.processWebhookEvent("pull_request", "delivery-1", payload);

        assertEquals(WebhookProcessingResult.ResultType.DUPLICATE, result.type());
        assertEquals(777L, result.job().getId());
        assertEquals("Duplicate webhook delivery; job already exists", result.message());
        verify(analysisJobService).createOrGetJob(any(JobCreateCommand.class));
    }
}
