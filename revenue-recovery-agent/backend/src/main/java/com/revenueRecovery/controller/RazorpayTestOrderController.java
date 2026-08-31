package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.CreateRazorpayTestOrderRequest;
import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.service.RazorpayOrderService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/razorpay/test/orders")
@CrossOrigin(origins = "*") // Local development only; restrict before deployment.
public class RazorpayTestOrderController {
    private final RazorpayOrderService service;

    public RazorpayTestOrderController(RazorpayOrderService service) {
        this.service = service;
    }

    @PostMapping
    public RazorpayTestOrderResponse create(
            @RequestBody(required = false) CreateRazorpayTestOrderRequest request) {
        return service.createTestOrder(request);
    }
}
