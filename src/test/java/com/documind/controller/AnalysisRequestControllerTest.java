package com.documind.controller;

import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.PullRequestAnalysisRequest;
import com.documind.service.AnalysisRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisRequestControllerTest {

    @Test
    void submitAnalysisRequestShouldDelegateToServiceAndReturnAccepted() {
        AnalysisRequestService service = mock(AnalysisRequestService.class);
        AnalysisRequestController controller = new AnalysisRequestController(service);

        PullRequestAnalysisRequest request = new PullRequestAnalysisRequest("octocat", "Hello-World", 42);
        AnalysisRequestResponse expectedResponse = new AnalysisRequestResponse(
                "ACCEPTED",
                "Analysis request accepted for processing",
                "octocat",
                "Hello-World",
                42,
                Instant.now()
        );

        when(service.processAnalysisRequest(request)).thenReturn(expectedResponse);

        ResponseEntity<AnalysisRequestResponse> response = controller.submitAnalysisRequest(request);

        assertNotNull(response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
        verify(service).processAnalysisRequest(request);
    }
}
