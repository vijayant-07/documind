package com.documind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RepositoryDetails(
        Long id,
        String name,
        @JsonProperty("full_name")
        String fullName,
        @JsonProperty("default_branch")
        String defaultBranch,
        UserDetails owner
) {
}
