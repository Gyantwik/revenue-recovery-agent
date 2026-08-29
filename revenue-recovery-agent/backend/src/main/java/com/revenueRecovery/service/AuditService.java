package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.ClassificationResult;
import com.revenueRecovery.model.DetectionResult;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
        return auditRecordRepository.save(execution);
    }

    public AuditRecord trackOutcome(AuditRecord auditRecord) {
        return auditRecord;
    }

    private String joinSignals(List<String> signals) {
        return signals == null ? "" : String.join("; ", signals);
    }

    private String stopOrEscalateReason(
            AuditRecord auditRecord,
            ClassificationResult classification) {
        if (auditRecord.getOutcome() == Outcome.STOPPED_CORRECTLY) {
            if (auditRecord.getRootCause() == RootCause.USER_CANCELLED) {
                return "User explicitly cancelled — policy forbids retry";
            }
            if (auditRecord.getRootCause() == RootCause.INCORRECT_PIN) {
                return "Authentication failure — policy forbids retry";
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
                return "Mandate expired — requires merchant review";
            }
            return "Policy requires manual review";
        }

        return null;
    }
}
