package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RazorpayCheckoutEventResponse(
        @JsonProperty("internal_request_id") String internalRequestId,
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        @JsonProperty("razorpay_payment_id") String razorpayPaymentId,
        @JsonProperty("event_type") String eventType,
        String status,
        Instant timestamp) {
}
