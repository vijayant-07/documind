package com.documind.service;

import com.documind.config.DocuMindProperties;
import com.documind.dto.BranchDetails;
import com.documind.dto.GitHubWebhookPayload;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.dto.PullRequestDetails;
import com.documind.dto.RepositoryDetails;
import com.documind.dto.WebhookProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebhookEventService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventService.class);
    private static final String PULL_REQUEST_EVENT = "pull_request";
    private static final String CLOSED_ACTION = "closed";

    private final DocuMindProperties properties;
    private final AnalysisJobService analysisJobService;

    public WebhookEventService(DocuMindProperties properties, AnalysisJobService analysisJobService) {
        this.properties = properties;
        this.analysisJobService = analysisJobService;
    }

    /**
     * Evaluates a GitHub webhook delivery, applies filtering rules (BR-1),
     * and idempotently enqueues/persists qualifying merged PR events (BR-2).
     */
    public WebhookProcessingResult processWebhookEvent(String eventType, String deliveryId, GitHubWebhookPayload payload) {
        // 1. Verify event type
        if (!PULL_REQUEST_EVENT.equalsIgnoreCase(eventType)) {
            log.info("Ignoring non-pull_request GitHub event: {}", eventType);
            return WebhookProcessingResult.ignored("Event type '" + eventType + "' is not supported");
        }

        if (payload == null) {
            log.warn("Null payload received for event {}", eventType);
            return WebhookProcessingResult.ignored("Missing or empty payload");
        }

        // 2. Verify PR action is closed
        if (!CLOSED_ACTION.equalsIgnoreCase(payload.action())) {
            log.info("Ignoring pull_request action: {}", payload.action());
            return WebhookProcessingResult.ignored("Pull request action '" + payload.action() + "' is not 'closed'");
        }

        PullRequestDetails pr = payload.pullRequest();
        if (pr == null) {
            log.warn("Missing pull_request object in closed PR event");
            return WebhookProcessingResult.ignored("Missing pull_request data in payload");
        }

        // 3. Verify PR was merged
        if (!Boolean.TRUE.equals(pr.merged())) {
            log.info("Ignoring pull request #{} because it was closed without being merged", pr.number());
            return WebhookProcessingResult.ignored("Pull request #" + pr.number() + " was closed unmerged");
        }

        // 4. Loop prevention: Ignore DocuMind-generated PRs
        String branchPrefix = properties.getGithub().getBranchPrefix();
        String prBodyMarker = properties.getGithub().getPrBodyMarker();

        BranchDetails head = pr.head();
        if (head != null && head.ref() != null && branchPrefix != null && !branchPrefix.isBlank()) {
            if (head.ref().startsWith(branchPrefix)) {
                log.info("Ignoring DocuMind-generated pull request branch: {}", head.ref());
                return WebhookProcessingResult.ignored("Ignoring DocuMind-generated pull request branch: " + head.ref());
            }
        }

        if (pr.body() != null && prBodyMarker != null && !prBodyMarker.isBlank()) {
            if (pr.body().contains(prBodyMarker)) {
                log.info("Ignoring DocuMind-generated pull request by body marker");
                return WebhookProcessingResult.ignored("Ignoring DocuMind-generated pull request with marker: " + prBodyMarker);
            }
        }

        // 5. Target / base branch filter
        BranchDetails base = pr.base();
        String baseBranch = base != null ? base.ref() : null;
        RepositoryDetails repo = payload.repository();
        String defaultBranch = repo != null ? repo.defaultBranch() : null;

        List<String> allowedBranches = properties.getGithub().getAllowedBranches();
        if (!isBranchAllowed(baseBranch, defaultBranch, allowedBranches)) {
            log.info("Ignoring PR #{} because base branch '{}' is not in allowed branches {}",
                    pr.number(), baseBranch, allowedBranches);
            return WebhookProcessingResult.ignored("Base branch '" + baseBranch + "' is not configured for documentation generation");
        }

        // 6. Valid qualifying merged PR: Persist job idempotently
        Long repoId = repo != null ? repo.id() : null;
        String repoFullName = repo != null ? repo.fullName() : "unknown/unknown";
        String mergeSha = pr.mergeCommitSha();
        String headSha = head != null ? head.sha() : null;
        String idempotencyKey = JobCreateCommand.buildWebhookIdempotencyKey(repoId, pr.number(), mergeSha);

        JobCreateCommand command = new JobCreateCommand(
                repoId,
                repoFullName,
                pr.number(),
                baseBranch,
                headSha,
                mergeSha,
                idempotencyKey,
                deliveryId
        );

        JobSubmissionResult result = analysisJobService.createOrGetJob(command);

        if (result.newlyCreated()) {
            log.info("Accepted qualifying PR merge event: {} PR #{} (job id={})",
                    repoFullName, pr.number(), result.job().getId());
            return WebhookProcessingResult.accepted(result.job());
        } else {
            log.info("Duplicate delivery for {} PR #{} (existing job id={})",
                    repoFullName, pr.number(), result.job().getId());
            return WebhookProcessingResult.duplicate(result.job());
        }
    }

    private boolean isBranchAllowed(String baseBranch, String defaultBranch, List<String> allowedBranches) {
        if (baseBranch == null || baseBranch.isBlank()) {
            return false;
        }

        // Check if explicitly configured in allowed branches
        if (allowedBranches != null && !allowedBranches.isEmpty()) {
            return allowedBranches.stream().anyMatch(baseBranch::equalsIgnoreCase);
        }

        // Default: only repository default branch
        return defaultBranch != null && baseBranch.equalsIgnoreCase(defaultBranch);
    }
}
