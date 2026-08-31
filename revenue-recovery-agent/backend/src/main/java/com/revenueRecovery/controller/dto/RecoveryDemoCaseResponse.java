package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record RecoveryDemoCaseResponse(
        @JsonProperty("event_id") String eventId,
        String type,
        @JsonProperty("failure_root_cause") String failureRootCause,
        BigDecimal amount,
        String currency,
        @JsonProperty("policy_action") String policyAction,
        @JsonProperty("recovery_status") String recoveryStatus,
        @JsonProperty("razorpay_mode") String razorpayMode,
        @JsonProperty("demo_only") boolean demoOnly) {
}
