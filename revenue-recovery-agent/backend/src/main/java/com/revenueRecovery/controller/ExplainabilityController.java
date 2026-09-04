package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.ExplainabilityResponse;
import com.revenueRecovery.service.ExplainabilityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*") // Local development only; restrict before deployment.
public class ExplainabilityController {
    private final ExplainabilityService service;
    public ExplainabilityController(ExplainabilityService service) { this.service = service; }
    @GetMapping("/{eventId}/explainability")
    public ExplainabilityResponse explain(@PathVariable String eventId) { return service.explain(eventId); }
}
