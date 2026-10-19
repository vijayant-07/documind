package com.documind.dto;

public record JobCreateCommand(
        Long repositoryId,
        String repositoryFullName,
        int pullRequestNumber,
        String baseBranch,
        String headSha,
        String mergeSha,
        String idempotencyKey,
        String githubDeliveryId
) {
    public static String buildWebhookIdempotencyKey(Long repositoryId, int prNumber, String mergeSha) {
        return repositoryId + ":" + prNumber + ":" + (mergeSha != null ? mergeSha : "unknown");
    }

    public static String buildManualIdempotencyKey(String repositoryFullName, int prNumber) {
        return "manual:" + repositoryFullName.toLowerCase() + ":" + prNumber;
    }
}
