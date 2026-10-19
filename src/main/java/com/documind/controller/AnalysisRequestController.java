package com.documind.controller;

import com.documind.dto.AnalysisRequestResponse;
import com.documind.dto.PullRequestAnalysisRequest;
import com.documind.service.AnalysisRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalysisRequestController {

    private final AnalysisRequestService analysisRequestService;

    public AnalysisRequestController(AnalysisRequestService analysisRequestService) {
        this.analysisRequestService = analysisRequestService;
    }

    @PostMapping("/analysis-requests")
    public ResponseEntity<AnalysisRequestResponse> submitAnalysisRequest(
            @Valid @RequestBody PullRequestAnalysisRequest request) {
        AnalysisRequestResponse response = analysisRequestService.processAnalysisRequest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
