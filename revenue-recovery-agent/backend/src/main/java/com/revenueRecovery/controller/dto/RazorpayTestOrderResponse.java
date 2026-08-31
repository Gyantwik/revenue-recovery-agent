package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RazorpayTestOrderResponse(
        @JsonProperty("internal_request_id") String internalRequestId,
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        long amount,
        String currency,
        String receipt,
        String status,
        String mode) {
}
