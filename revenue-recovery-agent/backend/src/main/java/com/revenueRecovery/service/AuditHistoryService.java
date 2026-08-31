package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditHistory;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditHistoryService {
    private final AuditHistoryRepository repository;

    public AuditHistoryService(AuditHistoryRepository repository) { this.repository = repository; }

    public AuditHistory append(AuditRecord record, LifecycleState previous, LifecycleState next,
            ActionTaken action, Outcome terminalOutcome, String reason, AuditActor actor, String key) {
        AuditHistory history = new AuditHistory();
        history.setEventId(record.getEventId());
        history.setTimestamp(Instant.now());
        history.setPreviousState(previous);
        history.setNewState(next);
        history.setRootCause(record.getRootCause());
        history.setClassificationConfidence(record.getClassificationConfidence());
        history.setPolicyRuleMatched(record.getPolicyRuleMatched());
        history.setActionTaken(action);
        history.setAttemptNumber(record.getAttemptNumber());
        history.setMaxAttemptsAllowed(record.getMaxAttemptsAllowed());
        history.setOutcomeIfTerminal(terminalOutcome);
        history.setReason(reason);
        history.setActor(actor);
        history.setIdempotencyKey(key);
        return repository.save(history);
    }

    public boolean hasHistory(String eventId) {
        return repository.countByEventId(eventId) > 0;
    }
}
