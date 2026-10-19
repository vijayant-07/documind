package com.documind.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisStatusTest {

    @Test
    void isTerminalShouldReturnTrueOnlyForTerminalStates() {
        assertTrue(AnalysisStatus.COMPLETED.isTerminal());
        assertTrue(AnalysisStatus.FAILED.isTerminal());
        assertTrue(AnalysisStatus.IGNORED.isTerminal());

        assertFalse(AnalysisStatus.RECEIVED.isTerminal());
        assertFalse(AnalysisStatus.QUEUED.isTerminal());
        assertFalse(AnalysisStatus.PROCESSING.isTerminal());
        assertFalse(AnalysisStatus.GENERATING_DOCUMENTATION.isTerminal());
        assertFalse(AnalysisStatus.CREATING_PULL_REQUEST.isTerminal());
        assertFalse(AnalysisStatus.RETRYABLE_FAILURE.isTerminal());
    }

    @Test
    void canTransitionToShouldAllowValidTransitions() {
        // Self transition
        assertTrue(AnalysisStatus.RECEIVED.canTransitionTo(AnalysisStatus.RECEIVED));

        // From RECEIVED
        assertTrue(AnalysisStatus.RECEIVED.canTransitionTo(AnalysisStatus.QUEUED));
        assertTrue(AnalysisStatus.RECEIVED.canTransitionTo(AnalysisStatus.PROCESSING));
        assertTrue(AnalysisStatus.RECEIVED.canTransitionTo(AnalysisStatus.IGNORED));
        assertTrue(AnalysisStatus.RECEIVED.canTransitionTo(AnalysisStatus.FAILED));

        // From QUEUED
        assertTrue(AnalysisStatus.QUEUED.canTransitionTo(AnalysisStatus.PROCESSING));
        assertTrue(AnalysisStatus.QUEUED.canTransitionTo(AnalysisStatus.IGNORED));

        // From PROCESSING
        assertTrue(AnalysisStatus.PROCESSING.canTransitionTo(AnalysisStatus.GENERATING_DOCUMENTATION));
        assertTrue(AnalysisStatus.PROCESSING.canTransitionTo(AnalysisStatus.CREATING_PULL_REQUEST));
        assertTrue(AnalysisStatus.PROCESSING.canTransitionTo(AnalysisStatus.COMPLETED));
        assertTrue(AnalysisStatus.PROCESSING.canTransitionTo(AnalysisStatus.RETRYABLE_FAILURE));
        assertTrue(AnalysisStatus.PROCESSING.canTransitionTo(AnalysisStatus.FAILED));

        // From GENERATING_DOCUMENTATION
        assertTrue(AnalysisStatus.GENERATING_DOCUMENTATION.canTransitionTo(AnalysisStatus.CREATING_PULL_REQUEST));
        assertTrue(AnalysisStatus.GENERATING_DOCUMENTATION.canTransitionTo(AnalysisStatus.COMPLETED));
        assertTrue(AnalysisStatus.GENERATING_DOCUMENTATION.canTransitionTo(AnalysisStatus.RETRYABLE_FAILURE));

        // From CREATING_PULL_REQUEST
        assertTrue(AnalysisStatus.CREATING_PULL_REQUEST.canTransitionTo(AnalysisStatus.COMPLETED));
        assertTrue(AnalysisStatus.CREATING_PULL_REQUEST.canTransitionTo(AnalysisStatus.RETRYABLE_FAILURE));

        // From RETRYABLE_FAILURE
        assertTrue(AnalysisStatus.RETRYABLE_FAILURE.canTransitionTo(AnalysisStatus.QUEUED));
        assertTrue(AnalysisStatus.RETRYABLE_FAILURE.canTransitionTo(AnalysisStatus.PROCESSING));
    }

    @Test
    void canTransitionToShouldDisallowInvalidTransitions() {
        // Terminal states cannot transition to anything other than themselves
        assertFalse(AnalysisStatus.COMPLETED.canTransitionTo(AnalysisStatus.PROCESSING));
        assertFalse(AnalysisStatus.FAILED.canTransitionTo(AnalysisStatus.RECEIVED));
        assertFalse(AnalysisStatus.IGNORED.canTransitionTo(AnalysisStatus.QUEUED));

        // Invalid forward jumps
        assertFalse(AnalysisStatus.RECEIVED.canTransitionTo(AnalysisStatus.COMPLETED));
        assertFalse(AnalysisStatus.QUEUED.canTransitionTo(AnalysisStatus.CREATING_PULL_REQUEST));
    }
}
