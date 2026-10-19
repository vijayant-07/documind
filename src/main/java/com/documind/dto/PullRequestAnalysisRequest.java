package com.documind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PullRequestAnalysisRequest(
        @NotBlank(message = "Owner must not be blank")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Owner contains invalid characters")
        @Size(max = 100, message = "Owner name must not exceed 100 characters")
        String owner,

        @NotBlank(message = "Repository must not be blank")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Repository contains invalid characters")
        @Size(max = 100, message = "Repository name must not exceed 100 characters")
        String repository,

        @NotNull(message = "Pull request number must not be null")
        @Positive(message = "Pull request number must be positive")
        Integer pullRequestNumber
) {
}
