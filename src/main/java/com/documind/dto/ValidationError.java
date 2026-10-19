package com.documind.dto;

public record ValidationError(
        String field,
        String message
) {
}
