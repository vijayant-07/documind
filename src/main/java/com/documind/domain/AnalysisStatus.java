package com.documind.domain;

public enum AnalysisStatus {
    RECEIVED,
    QUEUED,
    PROCESSING,
    GENERATING_DOCUMENTATION,
    CREATING_PULL_REQUEST,
    COMPLETED,
    RETRYABLE_FAILURE,
    FAILED,
    IGNORED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == IGNORED;
    }

    public boolean canTransitionTo(AnalysisStatus nextStatus) {
        if (this == nextStatus) {
            return true;
        }
        return switch (this) {
            case RECEIVED -> nextStatus == QUEUED || nextStatus == PROCESSING || nextStatus == IGNORED || nextStatus == FAILED;
            case QUEUED -> nextStatus == PROCESSING || nextStatus == IGNORED || nextStatus == FAILED;
            case PROCESSING -> nextStatus == GENERATING_DOCUMENTATION || nextStatus == CREATING_PULL_REQUEST || nextStatus == COMPLETED || nextStatus == RETRYABLE_FAILURE || nextStatus == FAILED || nextStatus == IGNORED;
            case GENERATING_DOCUMENTATION -> nextStatus == CREATING_PULL_REQUEST || nextStatus == COMPLETED || nextStatus == RETRYABLE_FAILURE || nextStatus == FAILED;
            case CREATING_PULL_REQUEST -> nextStatus == COMPLETED || nextStatus == RETRYABLE_FAILURE || nextStatus == FAILED;
            case RETRYABLE_FAILURE -> nextStatus == QUEUED || nextStatus == PROCESSING || nextStatus == FAILED;
            case COMPLETED, FAILED, IGNORED -> false;
        };
    }
}
