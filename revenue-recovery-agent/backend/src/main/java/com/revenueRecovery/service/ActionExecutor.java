package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class ActionExecutor {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final AuditRecordRepository auditRecordRepository;

    public ActionExecutor(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    public AuditRecord execute(Event event, PolicyDecision policyDecision) {
        String eventId = Objects.requireNonNull(event.getEventId(), "eventId must not be null");
        return auditRecordRepository.findByEventId(eventId)
                .orElseGet(() -> simulateAction(event, policyDecision));
    }

    private AuditRecord simulateAction(Event event, PolicyDecision policyDecision) {
        AuditRecord execution = new AuditRecord();
        ActionTaken action = policyDecision.getActionTaken();
        int maxAttempts = policyDecision.getMaxAttemptsAllowed();

        execution.setActionTaken(action);
        execution.setMaxAttemptsAllowed(maxAttempts);

        if (action == ActionTaken.NO_ACTION_STOP) {
            execution.setOutcome(Outcome.STOPPED_CORRECTLY);
            execution.setRecoveredAmount(ZERO);
            execution.setAttemptNumber(0);
            return execution;
        }

        if (action == ActionTaken.ESCALATE_MERCHANT) {
            execution.setOutcome(Outcome.ESCALATED);
            execution.setRecoveredAmount(ZERO);
            execution.setAttemptNumber(0);
            return execution;
        }

        int eventHash = event.getEventId().hashCode();
        boolean recovered = Math.floorMod(eventHash, 100) < 60;
        execution.setOutcome(recovered ? Outcome.RECOVERED : Outcome.NOT_RECOVERED);
        execution.setRecoveredAmount(recovered ? event.getAmount() : ZERO);

        if (maxAttempts > 0) {
            int recoveredAttempt = 1 + Math.floorMod(eventHash / 100, maxAttempts);
            execution.setAttemptNumber(recovered ? recoveredAttempt : maxAttempts);
        } else {
            execution.setAttemptNumber(0);
        }

        return execution;
    }
}
