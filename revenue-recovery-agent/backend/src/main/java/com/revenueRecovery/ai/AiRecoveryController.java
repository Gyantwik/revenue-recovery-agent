package com.revenueRecovery.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
// Local development only. Restrict allowed origins before deployment.
@CrossOrigin(origins = "*")
public class AiRecoveryController {

    private final AiRecoveryService aiRecoveryService;

    public AiRecoveryController(AiRecoveryService aiRecoveryService) {
        this.aiRecoveryService = aiRecoveryService;
    }

    @GetMapping("/{eventId}/ai-analysis")
    public ResponseEntity<AiRecoveryDtos.AiAnalysisResponse> analyzeFailure(
            @PathVariable String eventId) {
        return ResponseEntity.ok(aiRecoveryService.analyzeFailure(eventId));
    }

    @PostMapping("/{eventId}/ai-message")
    public ResponseEntity<AiRecoveryDtos.AiMessageResponse> generateRecoveryMessage(
            @PathVariable String eventId,
            @RequestBody(required = false) AiRecoveryDtos.AiMessageRequest request) {
        return ResponseEntity.ok(aiRecoveryService.generateRecoveryMessage(eventId, request));
    }
}
