package com.revenueRecovery.controller;

import com.revenueRecovery.service.RazorpayWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/razorpay")
@CrossOrigin(origins = "*") // Local test-mode demo only; restrict before deployment.
public class RazorpayWebhookController {
    private final RazorpayWebhookService service;
    public RazorpayWebhookController(RazorpayWebhookService service) { this.service = service; }

    @PostMapping(value = "/webhook", consumes = "application/json")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        RazorpayWebhookService.WebhookResult result = service.handle(payload, signature);
        return ResponseEntity.status(result.status()).body(Map.of("status", result.result()));
    }
}
