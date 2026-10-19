package com.documind.controller;

import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.PullRequestAnalysisRequest;
import com.documind.service.AnalysisRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisRequestController.class)
@Import(GlobalExceptionHandler.class)
class AnalysisRequestControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisRequestService analysisRequestService;

    @Test
    void submitAnalysisRequestWithValidDataShouldReturn202Accepted() throws Exception {
        AnalysisRequestResponse response = new AnalysisRequestResponse(
                "ACCEPTED",
                "Analysis request accepted for processing",
                "octocat",
                "Hello-World",
                42,
                Instant.now()
        );

        when(analysisRequestService.processAnalysisRequest(any(PullRequestAnalysisRequest.class)))
                .thenReturn(response);

        String validJson = """
                {
                  "owner": "octocat",
                  "repository": "Hello-World",
                  "pullRequestNumber": 42
                }
                """;

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value("Analysis request accepted for processing"))
                .andExpect(jsonPath("$.owner").value("octocat"))
                .andExpect(jsonPath("$.repository").value("Hello-World"))
                .andExpect(jsonPath("$.pullRequestNumber").value(42))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());

        verify(analysisRequestService).processAnalysisRequest(any(PullRequestAnalysisRequest.class));
    }

    @Test
    void submitAnalysisRequestWithBlankOwnerShouldReturn400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "owner": "",
                  "repository": "Hello-World",
                  "pullRequestNumber": 42
                }
                """;

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed for request"))
                .andExpect(jsonPath("$.errors[?(@.field == 'owner')]").exists());

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void submitAnalysisRequestWithInvalidOwnerCharactersShouldReturn400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "owner": "octo cat",
                  "repository": "Hello-World",
                  "pullRequestNumber": 42
                }
                """;

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'owner')].message").value("Owner contains invalid characters"));

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void submitAnalysisRequestWithBlankRepositoryShouldReturn400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "owner": "octocat",
                  "repository": "   ",
                  "pullRequestNumber": 42
                }
                """;

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'repository')]").exists());

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void submitAnalysisRequestWithNullPullRequestNumberShouldReturn400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "owner": "octocat",
                  "repository": "Hello-World"
                }
                """;

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'pullRequestNumber')]").exists());

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void submitAnalysisRequestWithNonPositivePullRequestNumberShouldReturn400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "owner": "octocat",
                  "repository": "Hello-World",
                  "pullRequestNumber": 0
                }
                """;

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == 'pullRequestNumber')].message").value("Pull request number must be positive"));

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void submitAnalysisRequestWithEmptyBodyShouldReturn400BadRequest() throws Exception {
        String emptyJson = "{}";

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void submitAnalysisRequestWithMalformedJsonShouldReturn400BadRequest() throws Exception {
        String malformedJson = "{owner: octocat, repository";

        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));

        verify(analysisRequestService, never()).processAnalysisRequest(any());
    }

    @Test
    void getAnalysisRequestsShouldReturn405MethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-requests")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void submitAnalysisRequestWithUnsupportedMediaTypeShouldReturn415UnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-requests")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("owner=octocat")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }
}
