package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransactionRecoveryStatusResponse(
        @JsonProperty("event_id") String eventId,
        String status,
        String message,
        @JsonProperty("is_recovered") boolean recovered,
        @JsonProperty("link_status") String linkStatus,
        String mode) {
}
