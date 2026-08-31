package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.RecoveryDemoCaseResponse;
import com.revenueRecovery.controller.dto.RecoveryLinkedOrderResponse;
import com.revenueRecovery.controller.dto.RecoveryPaymentStatusResponse;
import com.revenueRecovery.service.RecoveryPaymentLinkService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recovery")
@CrossOrigin(origins = "*") // Local development only; restrict before deployment.
public class RecoveryPaymentController {
    private final RecoveryPaymentLinkService service;

    public RecoveryPaymentController(RecoveryPaymentLinkService service) {
        this.service = service;
    }

    @GetMapping("/test-mode/demo-case")
    public RecoveryDemoCaseResponse getDemoCase() {
        return service.getDemoCase();
    }

    @PostMapping("/{eventId}/razorpay-test-order")
    public RecoveryLinkedOrderResponse createLinkedOrder(@PathVariable String eventId) {
        return service.createLinkedOrder(eventId);
    }

    @GetMapping("/{eventId}/razorpay-test-status")
    public RecoveryPaymentStatusResponse getStatus(@PathVariable String eventId) {
        return service.getStatus(eventId);
    }
}
