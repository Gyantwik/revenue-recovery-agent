package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.controller.dto.RazorpayCheckoutEventResponse;
import com.revenueRecovery.controller.dto.RazorpayTestConfigResponse;
import com.revenueRecovery.service.RazorpayCheckoutEventService;
import com.revenueRecovery.service.RazorpayTestConfigService;
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

    public RazorpayTestCheckoutController(RazorpayTestConfigService configService,
            RazorpayCheckoutEventService checkoutEventService) {
        this.configService = configService;
        this.checkoutEventService = checkoutEventService;
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
}
