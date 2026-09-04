package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.AuditHistory;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.model.enums.TransactionSource;
import com.revenueRecovery.model.enums.VerificationResult;

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
        @JsonProperty("stop_or_escalate_reason") String stopOrEscalateReason,
        @JsonProperty("lifecycle_state") LifecycleState lifecycleState,
        @JsonProperty("next_eligible_action_at") Instant nextEligibleActionAt,
        @JsonProperty("recovery_window_expires_at") Instant recoveryWindowExpiresAt,
        List<HistoryResponse> history,
        @JsonProperty("customer_ref") String customerRef,
        TransactionSource source,
        @JsonProperty("verification_result") VerificationResult verificationResult,
        String detail,
        @JsonProperty("gateway_error_reason") String gatewayErrorReason,
        @JsonProperty("gateway_order_id") String gatewayOrderId,
        @JsonProperty("gateway_payment_id") String gatewayPaymentId,
        @JsonProperty("escalation_reason") String escalationReason) {

    public static AuditRecordResponse from(AuditRecord record) {
        return from(record, List.of());
    }

    public static AuditRecordResponse from(AuditRecord record, List<AuditHistory> history) {
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
                record.getStopOrEscalateReason(),
                record.getLifecycleState(),
                record.getNextEligibleActionAt(),
                record.getRecoveryWindowExpiresAt(),
                history.stream().map(HistoryResponse::from).toList(),
                record.getCustomerRef(), record.getSource(), record.getVerificationResult(),
                record.getDetail(), record.getGatewayErrorReason(), record.getGatewayOrderId(),
                record.getGatewayPaymentId(), record.getEscalationReason());
    }

    private static List<String> splitSignals(String signals) {
        if (signals == null || signals.isBlank()) {
            return List.of();
        }
        return Arrays.stream(signals.split("; ", -1)).toList();
    }

    public record HistoryResponse(
            Instant timestamp,
            @JsonProperty("previous_state") LifecycleState previousState,
            @JsonProperty("new_state") LifecycleState newState,
            @JsonProperty("root_cause") RootCause rootCause,
            @JsonProperty("classification_confidence") BigDecimal classificationConfidence,
            @JsonProperty("policy_rule_matched") String policyRuleMatched,
            @JsonProperty("action_taken") ActionTaken actionTaken,
            @JsonProperty("attempt_number") Integer attemptNumber,
            @JsonProperty("max_attempts_allowed") Integer maxAttemptsAllowed,
            @JsonProperty("outcome_if_terminal") Outcome outcomeIfTerminal,
            String reason,
            AuditActor actor,
            @JsonProperty("idempotency_key_or_action_sequence_key") String idempotencyKey) {
        static HistoryResponse from(AuditHistory history) {
            return new HistoryResponse(history.getTimestamp(), history.getPreviousState(), history.getNewState(),
                    history.getRootCause(), history.getClassificationConfidence(), history.getPolicyRuleMatched(),
                    history.getActionTaken(), history.getAttemptNumber(), history.getMaxAttemptsAllowed(),
                    history.getOutcomeIfTerminal(), history.getReason(), history.getActor(), history.getIdempotencyKey());
        }
    }
}
