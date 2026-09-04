package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RazorpayCheckoutEventRequest(
        @JsonProperty("internal_request_id") String internalRequestId,
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        @JsonProperty("razorpay_payment_id") String razorpayPaymentId,
        @JsonProperty("razorpay_signature") String razorpaySignature,
        @JsonProperty("event_type") String eventType,
        String reason,
        @JsonProperty("customer_ref") String customerRef,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_description") String errorDescription,
        @JsonProperty("error_source") String errorSource,
        @JsonProperty("error_step") String errorStep,
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("simulated_connection") Boolean simulatedConnection) {
}
