package com.documind.controller;

import com.documind.dto.ErrorResponse;
import com.documind.dto.GitHubWebhookPayload;
import com.documind.dto.WebhookProcessingResult;
import com.documind.dto.WebhookResponse;
import com.documind.service.WebhookEventService;
import com.documind.service.WebhookSignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/webhooks")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookEventService webhookEventService;
    private final JsonMapper jsonMapper;

    public GitHubWebhookController(
            WebhookSignatureVerifier signatureVerifier,
            WebhookEventService webhookEventService,
            JsonMapper jsonMapper
    ) {
        this.signatureVerifier = signatureVerifier;
        this.webhookEventService = webhookEventService;
        this.jsonMapper = jsonMapper != null ? jsonMapper : JsonMapper.builder().build();
    }

    @PostMapping("/github")
    public ResponseEntity<?> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody(required = false) byte[] rawBody,
            HttpServletRequest request
    ) {
        byte[] bodyBytes = rawBody != null ? rawBody : new byte[0];

        // 1. Verify HMAC SHA-256 signature against raw bytes
        if (signature == null || signature.isBlank() || !signatureVerifier.verifySignature(signature, bodyBytes)) {
            log.warn("Unauthorized webhook request: missing or invalid X-Hub-Signature-256");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Invalid or missing webhook signature", request.getRequestURI())
            );
        }

        // 2. Verify required event header
        if (eventType == null || eventType.isBlank()) {
            log.warn("Bad webhook request: missing X-GitHub-Event header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ErrorResponse.of(HttpStatus.BAD_REQUEST, "Missing required X-GitHub-Event header", request.getRequestURI())
            );
        }

        // 3. Parse JSON payload from raw bytes
        if (bodyBytes.length == 0) {
            log.warn("Bad webhook request: empty payload body");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ErrorResponse.of(HttpStatus.BAD_REQUEST, "Malformed JSON webhook payload", request.getRequestURI())
            );
        }

        GitHubWebhookPayload payload;
        try {
            payload = jsonMapper.readValue(bodyBytes, GitHubWebhookPayload.class);
        } catch (JacksonException e) {
            log.warn("Failed to parse GitHub webhook payload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ErrorResponse.of(HttpStatus.BAD_REQUEST, "Malformed JSON webhook payload", request.getRequestURI())
            );
        }

        // 4. Process event and determine action
        WebhookProcessingResult result = webhookEventService.processWebhookEvent(eventType, deliveryId, payload);

        return switch (result.type()) {
            case ACCEPTED -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    WebhookResponse.accepted(result.job().getId(), result.job().getIdempotencyKey())
            );
            case DUPLICATE -> ResponseEntity.ok(
                    WebhookResponse.duplicate(result.job().getId(), result.job().getIdempotencyKey())
            );
            case IGNORED -> ResponseEntity.ok(
                    WebhookResponse.ignored(result.message())
            );
        };
    }
}
