package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record AuditRecordResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_type") String caseType,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        @JsonProperty("is_at_risk") Boolean isAtRisk,
        @JsonProperty("risk_amount") BigDecimal riskAmount,
        @JsonProperty("root_cause") RootCause rootCause,
        @JsonProperty("classification_confidence") BigDecimal classificationConfidence,
        @JsonProperty("signals_used") List<String> signalsUsed,
        @JsonProperty("policy_rule_matched") String policyRuleMatched,
        @JsonProperty("action_taken") ActionTaken actionTaken,
        @JsonProperty("attempt_number") Integer attemptNumber,
        @JsonProperty("max_attempts_allowed") Integer maxAttemptsAllowed,
        Outcome outcome,
        @JsonProperty("recovered_amount") BigDecimal recoveredAmount,
        @JsonProperty("stop_or_escalate_reason") String stopOrEscalateReason) {

    public static AuditRecordResponse from(AuditRecord record) {
        return new AuditRecordResponse(
                record.getEventId(),
                record.getCaseType(),
                record.getAmount(),
                record.getCurrency(),
                record.getTimestamp(),
                record.getIsAtRisk(),
                record.getRiskAmount(),
                record.getRootCause(),
                record.getClassificationConfidence(),
                splitSignals(record.getSignalsUsed()),
                record.getPolicyRuleMatched(),
                record.getActionTaken(),
                record.getAttemptNumber(),
                record.getMaxAttemptsAllowed(),
                record.getOutcome(),
                record.getRecoveredAmount(),
                record.getStopOrEscalateReason());
    }

    private static List<String> splitSignals(String signals) {
        if (signals == null || signals.isBlank()) {
            return List.of();
        }
        return Arrays.stream(signals.split("; ", -1)).toList();
    }
}
