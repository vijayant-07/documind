package com.documind.service;

import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.PullRequestAnalysisRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnalysisRequestServiceTest {

    private final AnalysisRequestService service = new AnalysisRequestService();

    @Test
    void processAnalysisRequestShouldReturnAcceptedResponse() {
        PullRequestAnalysisRequest request = new PullRequestAnalysisRequest("octocat", "Hello-World", 42);

        AnalysisRequestResponse response = service.processAnalysisRequest(request);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.status());
        assertEquals("Analysis request accepted for processing", response.message());
        assertEquals("octocat", response.owner());
        assertEquals("Hello-World", response.repository());
        assertEquals(42, response.pullRequestNumber());
        assertNotNull(response.receivedAt());
    }
}
