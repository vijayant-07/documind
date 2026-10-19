package com.documind.service;

import com.documind.domain.AnalysisJob;
import com.documind.domain.AnalysisStatus;
import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.JobCreateCommand;
import com.documind.dto.JobSubmissionResult;
import com.documind.dto.PullRequestAnalysisRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisRequestServiceTest {

    private AnalysisJobService analysisJobService;
    private AnalysisRequestService service;

    @BeforeEach
    void setUp() {
        analysisJobService = mock(AnalysisJobService.class);
        service = new AnalysisRequestService(analysisJobService);
    }

    @Test
    void processAnalysisRequestShouldReturnAcceptedResponseForNewJob() {
        PullRequestAnalysisRequest request = new PullRequestAnalysisRequest("octocat", "Hello-World", 42);

        AnalysisJob mockJob = new AnalysisJob(
                null,
                "octocat/Hello-World",
                42,
                null,
                null,
                null,
                "manual:octocat/hello-world:42",
                null,
                AnalysisStatus.RECEIVED
        );
        mockJob.setId(101L);

        when(analysisJobService.createOrGetJob(any(JobCreateCommand.class)))
                .thenReturn(new JobSubmissionResult(mockJob, true));

        AnalysisRequestResponse response = service.processAnalysisRequest(request);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.status());
        assertEquals("Analysis request accepted for processing", response.message());
        assertEquals(101L, response.jobId());
        assertEquals("octocat", response.owner());
        assertEquals("Hello-World", response.repository());
        assertEquals(42, response.pullRequestNumber());
        assertNotNull(response.receivedAt());

        verify(analysisJobService).createOrGetJob(any(JobCreateCommand.class));
    }

    @Test
    void processAnalysisRequestShouldHandleDuplicateJob() {
        PullRequestAnalysisRequest request = new PullRequestAnalysisRequest("octocat", "Hello-World", 42);

        AnalysisJob mockJob = new AnalysisJob(
                null,
                "octocat/Hello-World",
                42,
                null,
                null,
                null,
                "manual:octocat/hello-world:42",
                null,
                AnalysisStatus.RECEIVED
        );
        mockJob.setId(101L);

        when(analysisJobService.createOrGetJob(any(JobCreateCommand.class)))
                .thenReturn(new JobSubmissionResult(mockJob, false));

        AnalysisRequestResponse response = service.processAnalysisRequest(request);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.status());
        assertEquals("Analysis job already exists for this pull request", response.message());
        assertEquals(101L, response.jobId());

        verify(analysisJobService).createOrGetJob(any(JobCreateCommand.class));
    }
}
