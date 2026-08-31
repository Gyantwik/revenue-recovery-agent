package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecoveryPaymentStatusResponse(
        @JsonProperty("event_id") String eventId,
        boolean eligible,
        @JsonProperty("recovery_status") String recoveryStatus,
        @JsonProperty("link_status") String linkStatus,
        @JsonProperty("internal_request_id") String internalRequestId,
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        @JsonProperty("razorpay_payment_id") String razorpayPaymentId,
        @JsonProperty("verified_at") Instant verifiedAt,
        @JsonProperty("recovered_at") Instant recoveredAt,
        String mode,
        @JsonProperty("audit_history") List<RecoveryAuditEntryResponse> auditHistory) {

    public record RecoveryAuditEntryResponse(
            Instant timestamp,
            String action,
            String outcome,
            String reason,
            String actor) {
    }
}
