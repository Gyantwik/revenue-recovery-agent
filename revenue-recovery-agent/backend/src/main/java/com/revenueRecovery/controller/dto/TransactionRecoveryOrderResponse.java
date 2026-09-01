package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.enums.RecoveryCheckoutAction;

public record TransactionRecoveryOrderResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("internal_request_id") String internalRequestId,
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        long amount,
        String currency,
        String receipt,
        @JsonProperty("recovery_action") RecoveryCheckoutAction recoveryAction,
        @JsonProperty("link_status") String linkStatus,
        @JsonProperty("recovery_status") String recoveryStatus,
        String mode) {
}
