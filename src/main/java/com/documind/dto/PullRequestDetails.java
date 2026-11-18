package com.documind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestDetails(
        int number,
        String title,
        String body,
        Boolean merged,
        BranchDetails head,
        BranchDetails base,
        @JsonProperty("merge_commit_sha")
        String mergeCommitSha,
        UserDetails user
) {
}
