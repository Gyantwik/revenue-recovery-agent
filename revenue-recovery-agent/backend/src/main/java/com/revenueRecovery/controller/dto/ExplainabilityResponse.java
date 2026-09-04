package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.AuditHistory;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Read-only, evidence-backed explanation of an existing recovery decision. */
public record ExplainabilityResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_type") String caseType,
        @JsonProperty("signals_used") List<String> signalsUsed,
        @JsonProperty("root_cause") RootCause rootCause,
        @JsonProperty("classification_confidence") BigDecimal classificationConfidence,
        @JsonProperty("policy_rule_matched") String policyRuleMatched,
        @JsonProperty("allowed_action") ActionTaken allowedAction,
        @JsonProperty("attempt_number") Integer attemptNumber,
        @JsonProperty("max_attempts_allowed") Integer maxAttemptsAllowed,
        @JsonProperty("was_blocked") boolean wasBlocked,
        @JsonProperty("block_reason") String blockReason,
        Outcome outcome,
        @JsonProperty("recovered_amount") BigDecimal recoveredAmount,
        @JsonProperty("decision_trace") List<AgentDecisionTraceResponse> decisionTrace,
        List<AuditRecordResponse.HistoryResponse> history) {
    public static ExplainabilityResponse build(AuditRecord record,
            List<AgentDecisionTraceResponse> trace, List<AuditHistory> histories) {
        boolean blocked = record.getActionTaken() == ActionTaken.NO_ACTION_STOP
                || record.getOutcome() == Outcome.ESCALATED
                || record.getOutcome() == Outcome.STOPPED_CORRECTLY;
        String reason = record.getStopOrEscalateReason() != null
                ? record.getStopOrEscalateReason() : record.getEscalationReason();
        return new ExplainabilityResponse(record.getEventId(), record.getCaseType(),
                splitSignals(record.getSignalsUsed()), record.getRootCause(),
                record.getClassificationConfidence(), record.getPolicyRuleMatched(), record.getActionTaken(),
                record.getAttemptNumber(), record.getMaxAttemptsAllowed(), blocked, blocked ? reason : null,
                record.getOutcome(), record.getRecoveredAmount(), trace,
                histories.stream().map(AuditRecordResponse.HistoryResponse::from).toList());
    }
    private static List<String> splitSignals(String signals) {
        return signals == null || signals.isBlank() ? List.of() : Arrays.stream(signals.split("; ", -1)).toList();
    }
}
