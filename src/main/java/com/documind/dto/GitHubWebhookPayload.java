package com.documind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWebhookPayload(
        String action,
        Integer number,
        @JsonProperty("pull_request")
        PullRequestDetails pullRequest,
        RepositoryDetails repository,
        UserDetails sender
) {
}
