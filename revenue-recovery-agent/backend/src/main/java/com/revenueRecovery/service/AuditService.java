package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.ClassificationResult;
import com.revenueRecovery.model.DetectionResult;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.model.enums.TransactionSource;
import com.revenueRecovery.model.enums.VerificationResult;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AuditService {

    private final AuditRecordRepository auditRecordRepository;

    public AuditService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    public AuditRecord logDecision(
            Event event,
            DetectionResult detection,
            ClassificationResult classification,
            PolicyDecision policy,
            AuditRecord execution) {
        execution.setEventId(event.getEventId());
        execution.setCaseType(event.getCaseType());
        execution.setAmount(event.getAmount());
        execution.setCurrency(event.getCurrency());
        execution.setTimestamp(event.getTimestamp());
        execution.setIsAtRisk(detection.isAtRisk());
        execution.setRiskAmount(detection.getRiskAmount());
        execution.setRootCause(policy.getRootCause());
        execution.setClassificationConfidence(
                java.math.BigDecimal.valueOf(classification.getConfidence()));
        execution.setSignalsUsed(joinSignals(event.getSignalsUsed()));
        execution.setPolicyRuleMatched(policy.getPolicyRuleMatched());
        execution.setActionTaken(policy.getActionTaken());
        execution.setMaxAttemptsAllowed(policy.getMaxAttemptsAllowed());
        execution.setStopOrEscalateReason(stopOrEscalateReason(execution, classification));
        execution.setSource(TransactionSource.SEEDED_REFERENCE);
        execution.setDetail("{\"classification_method\":\"" + classification.getMethod()
                + "\",\"signals\":\"" + escape(joinSignals(event.getSignalsUsed())) + "\"}");
        if (policy.getActionTaken() == com.revenueRecovery.model.enums.ActionTaken.VERIFY_STATUS) {
            execution.setVerificationResult(execution.getOutcome() == Outcome.RECOVERED
                    ? VerificationResult.CONFIRMED_SUCCESS : VerificationResult.CONFIRMED_FAILED_RETRY_BLOCKED);
            if (execution.getOutcome() != Outcome.RECOVERED) {
                execution.setEscalationReason("BANK_PENDING_FINAL_STATUS_UNKNOWN");
            }
        }
        return auditRecordRepository.save(execution);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public AuditRecord trackOutcome(AuditRecord auditRecord) {
        return auditRecord;
    }

    private String joinSignals(List<String> signals) {
        return signals == null
                ? ""
                : signals.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("; "));
    }

    private String stopOrEscalateReason(
            AuditRecord auditRecord,
            ClassificationResult classification) {
        if (auditRecord.getOutcome() == Outcome.STOPPED_CORRECTLY) {
            if (auditRecord.getRootCause() == RootCause.USER_CANCELLED) {
                return "User explicitly cancelled — policy forbids retry";
            }
            if (auditRecord.getRootCause() == RootCause.INCORRECT_PIN) {
                return "Authentication failure — customer must initiate a fresh payment";
            }
            return "Policy requires processing to stop";
        }

        if (auditRecord.getOutcome() == Outcome.ESCALATED) {
            if (classification.getConfidence() < 0.75) {
                return "Low classification confidence — routed to manual review";
            }
            if (auditRecord.getRootCause() == RootCause.MERCHANT_GATEWAY_ISSUE) {
                return "Merchant/gateway-side issue — requires manual review";
            }
            if (auditRecord.getRootCause() == RootCause.MANDATE_EXPIRED) {
                return "Mandate expired — fresh customer mandate/consent required";
            }
            if (auditRecord.getRootCause() == RootCause.PAYMENT_PENDING) {
                return "Original payment status must be verified to avoid a duplicate debit";
            }
            return "Policy requires manual review";
        }

        return null;
    }
}
