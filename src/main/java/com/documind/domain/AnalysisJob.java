package com.documind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "repository_full_name", nullable = false)
    private String repositoryFullName;

    @Column(name = "pull_request_number", nullable = false)
    private int pullRequestNumber;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "head_sha", length = 64)
    private String headSha;

    @Column(name = "merge_sha", length = 64)
    private String mergeSha;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "github_delivery_id", length = 100)
    private String githubDeliveryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AnalysisStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "documentation_pull_request_number")
    private Integer documentationPullRequestNumber;

    @Column(name = "documentation_pull_request_url", length = 500)
    private String documentationPullRequestUrl;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AnalysisJob() {
    }

    public AnalysisJob(
            Long repositoryId,
            String repositoryFullName,
            int pullRequestNumber,
            String baseBranch,
            String headSha,
            String mergeSha,
            String idempotencyKey,
            String githubDeliveryId,
            AnalysisStatus status
    ) {
        this.repositoryId = repositoryId;
        this.repositoryFullName = repositoryFullName;
        this.pullRequestNumber = pullRequestNumber;
        this.baseBranch = baseBranch;
        this.headSha = headSha;
        this.mergeSha = mergeSha;
        this.idempotencyKey = idempotencyKey;
        this.githubDeliveryId = githubDeliveryId;
        this.status = status != null ? status : AnalysisStatus.RECEIVED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = AnalysisStatus.RECEIVED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // State transition methods

    public void transitionTo(AnalysisStatus targetStatus) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Illegal status transition from " + this.status + " to " + targetStatus
                            + " for job id " + this.id
            );
        }
        this.status = targetStatus;
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        transitionTo(AnalysisStatus.PROCESSING);
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        this.attemptCount++;
    }

    public void markCompleted(Integer docPrNumber, String docPrUrl) {
        transitionTo(AnalysisStatus.COMPLETED);
        this.documentationPullRequestNumber = docPrNumber;
        this.documentationPullRequestUrl = docPrUrl;
        this.completedAt = Instant.now();
    }

    public void markRetryableFailure(String error, Instant nextAttemptAt) {
        transitionTo(AnalysisStatus.RETRYABLE_FAILURE);
        this.lastError = sanitizeError(error);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed(String error) {
        transitionTo(AnalysisStatus.FAILED);
        this.lastError = sanitizeError(error);
        this.completedAt = Instant.now();
    }

    public void markIgnored(String reason) {
        transitionTo(AnalysisStatus.IGNORED);
        this.lastError = sanitizeError(reason);
        this.completedAt = Instant.now();
    }

    private String sanitizeError(String error) {
        if (error == null) {
            return null;
        }
        // Cap error summary to 2000 chars to avoid unbounded DB storage
        return error.length() > 2000 ? error.substring(0, 1997) + "..." : error;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public void setRepositoryFullName(String repositoryFullName) {
        this.repositoryFullName = repositoryFullName;
    }

    public int getPullRequestNumber() {
        return pullRequestNumber;
    }

    public void setPullRequestNumber(int pullRequestNumber) {
        this.pullRequestNumber = pullRequestNumber;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getMergeSha() {
        return mergeSha;
    }

    public void setMergeSha(String mergeSha) {
        this.mergeSha = mergeSha;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getGithubDeliveryId() {
        return githubDeliveryId;
    }

    public void setGithubDeliveryId(String githubDeliveryId) {
        this.githubDeliveryId = githubDeliveryId;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Integer getDocumentationPullRequestNumber() {
        return documentationPullRequestNumber;
    }

    public void setDocumentationPullRequestNumber(Integer documentationPullRequestNumber) {
        this.documentationPullRequestNumber = documentationPullRequestNumber;
    }

    public String getDocumentationPullRequestUrl() {
        return documentationPullRequestUrl;
    }

    public void setDocumentationPullRequestUrl(String documentationPullRequestUrl) {
        this.documentationPullRequestUrl = documentationPullRequestUrl;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalysisJob that = (AnalysisJob) o;
        return Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey);
    }
}
