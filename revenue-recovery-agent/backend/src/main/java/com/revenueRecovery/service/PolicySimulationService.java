package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.PolicyImpactResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Read-only policy impact projection; it never creates a payment or changes a record. */
@Service
public class PolicySimulationService {
    private final AuditRecordRepository records; private final PolicyEngine policy;
    public PolicySimulationService(AuditRecordRepository records, PolicyEngine policy) { this.records = records; this.policy = policy; }
    public PolicyImpactResponse simulate(RootCause filter) {
        List<AuditRecord> cases = records.findAll().stream().filter(r -> filter == null || cause(r) == filter).toList();
        int eligible = 0, blocked = 0, atRisk = 0, notAtRisk = 0;
        BigDecimal recoverable = BigDecimal.ZERO, recovered = BigDecimal.ZERO, blockedAmount = BigDecimal.ZERO;
        for (AuditRecord record : cases) {
            if (!Boolean.TRUE.equals(record.getIsAtRisk())) {
                notAtRisk++;
                continue; // Settled payments never enter a recovery-policy projection.
            }
            atRisk++;
            BigDecimal risk = value(record.getRiskAmount());
            if (policy.decide(cause(record)).getActionTaken() == ActionTaken.NO_ACTION_STOP) { blocked++; blockedAmount = blockedAmount.add(risk); }
            else { eligible++; recoverable = recoverable.add(risk); recovered = recovered.add(value(record.getRecoveredAmount())); }
        }
        PolicyDecision decision = filter == null ? null : policy.decide(filter);
        BigDecimal rate = recoverable.signum() == 0 ? BigDecimal.ZERO.setScale(4) : recovered.divide(recoverable, 4, RoundingMode.HALF_UP);
        return new PolicyImpactResponse(filter, decision == null ? null : decision.getPolicyRuleMatched(),
                decision == null ? null : decision.getActionTaken(), eligible, recoverable, rate, blocked, blockedAmount,
                atRisk, notAtRisk, cases.size());
    }
    public List<PolicyImpactResponse> simulateAll() { return Arrays.stream(RootCause.values()).map(this::simulate).toList(); }
    private RootCause cause(AuditRecord record) { return record.getRootCause() == null ? RootCause.UNKNOWN : record.getRootCause(); }
    private BigDecimal value(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
}
