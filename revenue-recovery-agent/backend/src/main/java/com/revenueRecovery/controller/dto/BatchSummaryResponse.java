package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.enums.RootCause;

import java.math.BigDecimal;
import java.util.List;

public record BatchSummaryResponse(
        @JsonProperty("total_at_risk") BigDecimal totalAtRisk,
        @JsonProperty("total_recovered") BigDecimal totalRecovered,
        @JsonProperty("recovery_rate") BigDecimal recoveryRate,
        @JsonProperty("total_cases") int totalCases,
        @JsonProperty("by_cause") List<CauseSummary> byCause,
        @JsonProperty("escalated_summary") List<EscalatedSummary> escalatedSummary) {

    public record CauseSummary(
            @JsonProperty("root_cause") RootCause rootCause,
            int count,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("recovered_amount") BigDecimal recoveredAmount) {
    }

    public record EscalatedSummary(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("root_cause") RootCause rootCause,
            BigDecimal amount,
            @JsonProperty("stop_or_escalate_reason") String stopOrEscalateReason) {
    }
}
