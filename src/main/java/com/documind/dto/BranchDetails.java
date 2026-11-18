package com.documind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BranchDetails(
        String ref,
        String sha
) {
}
