package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.controller.dto.RazorpayCheckoutEventResponse;
import com.revenueRecovery.controller.dto.RazorpayTestConfigResponse;
import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationRequest;
import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationResponse;
import com.revenueRecovery.service.RazorpayCheckoutEventService;
import com.revenueRecovery.service.RazorpayTestConfigService;
import com.revenueRecovery.service.RazorpaySignatureVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/razorpay/test")
@CrossOrigin(origins = "*") // Local development only; restrict before deployment.
public class RazorpayTestCheckoutController {
    private final RazorpayTestConfigService configService;
    private final RazorpayCheckoutEventService checkoutEventService;
    private final RazorpaySignatureVerificationService verificationService;

    public RazorpayTestCheckoutController(RazorpayTestConfigService configService,
            RazorpayCheckoutEventService checkoutEventService,
            RazorpaySignatureVerificationService verificationService) {
        this.configService = configService;
        this.checkoutEventService = checkoutEventService;
        this.verificationService = verificationService;
    }

    @GetMapping("/config")
    public RazorpayTestConfigResponse config() {
        return configService.getCheckoutConfig();
    }

    @PostMapping("/checkout-events")
    public RazorpayCheckoutEventResponse recordCheckoutEvent(
            @RequestBody(required = false) RazorpayCheckoutEventRequest request) {
        return checkoutEventService.record(request);
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<RazorpayPaymentVerificationResponse> verifyPayment(
            @RequestBody(required = false) RazorpayPaymentVerificationRequest request) {
        RazorpaySignatureVerificationService.VerificationOutcome outcome = verificationService.verify(request);
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.response());
    }
}
