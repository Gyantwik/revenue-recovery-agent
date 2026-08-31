package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.ActionResultResponse;
import com.revenueRecovery.model.*;
import com.revenueRecovery.model.enums.*;
import com.revenueRecovery.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class RecoveryActionService {
    private final AuditRecordRepository auditRepository;
    private final EventRepository eventRepository;
    private final AuditHistoryRepository historyRepository;
    private final PolicyEngine policyEngine;
    private final RecoveryStateMachine stateMachine;
    private final ActionExecutor actionExecutor;
    private final AuditHistoryService historyService;

    public RecoveryActionService(AuditRecordRepository auditRepository, EventRepository eventRepository,
            AuditHistoryRepository historyRepository, PolicyEngine policyEngine,
            RecoveryStateMachine stateMachine, ActionExecutor actionExecutor, AuditHistoryService historyService) {
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
        this.historyRepository = historyRepository;
        this.policyEngine = policyEngine;
        this.stateMachine = stateMachine;
        this.actionExecutor = actionExecutor;
        this.historyService = historyService;
    }

    @Transactional(noRollbackFor = RecoveryActionBlockedException.class)
    public ActionResultResponse triggerRetry(String eventId, String suppliedKey) {
        AuditRecord record = auditRepository.findByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        ActionTaken allowed = policyEngine.decide(record.getRootCause()).getActionTaken();
        ActionTaken requested = allowed == ActionTaken.SCHEDULE_MANDATE_RETRY
                ? ActionTaken.SCHEDULE_MANDATE_RETRY : ActionTaken.RETRY_PAYMENT;
        return trigger(eventId, requested, suppliedKey);
    }

    @Transactional(noRollbackFor = RecoveryActionBlockedException.class)
    public ActionResultResponse trigger(String eventId, ActionTaken requested, String suppliedKey) {
        AuditRecord record = auditRepository.findByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        Event event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        PolicyDecision policy = policyEngine.decide(record.getRootCause());
        int attempt = policy.getMaxAttemptsAllowed() == 0 ? 0 : value(record.getAttemptNumber()) + 1;
        String key = suppliedKey == null || suppliedKey.isBlank()
                ? eventId + ":manual:" + requested.toJson() + ":" + attempt
                : eventId + ":manual:" + suppliedKey.trim();
        try {
            stateMachine.validateAction(record, policy, requested, attempt, Instant.now(),
                    historyRepository.existsByIdempotencyKey(key));
        } catch (RecoveryActionBlockedException exception) {
            if (exception.getMessage().contains("window expired") && !record.getLifecycleState().isTerminal()) {
                LifecycleState previous = record.getLifecycleState();
                record.setLifecycleState(LifecycleState.RETRY_EXHAUSTED);
                record.setOutcome(Outcome.NOT_RECOVERED);
                record.setStopOrEscalateReason("30-minute synthetic recovery window expired");
                auditRepository.save(record);
                historyService.append(record, previous, LifecycleState.RETRY_EXHAUSTED, requested,
                        Outcome.NOT_RECOVERED, exception.getMessage(), AuditActor.MERCHANT_MANUAL, key + ":blocked");
            }
            throw exception;
        }

        LifecycleState previous = record.getLifecycleState();
        LifecycleState actionState = actionState(requested);
        if (previous != actionState) stateMachine.validateTransition(previous, actionState);
        record.setLifecycleState(actionState);
        record.setAttemptNumber(attempt);
        historyService.append(record, previous, actionState, requested, null,
                "Merchant-triggered synthetic/test-mode action accepted", AuditActor.MERCHANT_MANUAL, key);

        AuditRecord execution = actionExecutor.executeAuthorized(event, policy, attempt);
        record.setOutcome(execution.getOutcome());
        record.setRecoveredAmount(execution.getRecoveredAmount());
        LifecycleState finalState = finalState(record, policy);
        LifecycleState outcomePrevious = record.getLifecycleState();
        record.setLifecycleState(finalState);
        if (finalState == LifecycleState.RETRY_SCHEDULED) {
            record.setNextEligibleActionAt(Instant.now());
        }
        auditRepository.save(record);
        historyService.append(record, outcomePrevious, finalState, null,
                finalState.isTerminal() ? record.getOutcome() : null,
                "Synthetic/test-mode action outcome recorded", AuditActor.MERCHANT_MANUAL, key + ":result");
        return new ActionResultResponse(eventId, finalState, requested,
                "Synthetic/test-mode action processed under deterministic policy", record.getNextEligibleActionAt());
    }

    private LifecycleState actionState(ActionTaken action) {
        return switch (action) {
            case RETRY_PAYMENT, SCHEDULE_MANDATE_RETRY -> LifecycleState.RETRY_SCHEDULED;
            case VERIFY_STATUS -> LifecycleState.VERIFYING_PAYMENT;
            case SEND_ALT_PAYMENT_LINK, SEND_RECOVERY_LINK -> LifecycleState.RECOVERY_LINK_SENT;
            case ESCALATE_MERCHANT -> LifecycleState.ESCALATED;
            case NO_ACTION_STOP -> LifecycleState.STOPPED;
        };
    }

    private LifecycleState finalState(AuditRecord record, PolicyDecision policy) {
        if (record.getOutcome() == Outcome.RECOVERED) return LifecycleState.RECOVERED;
        if (record.getOutcome() == Outcome.ESCALATED) return LifecycleState.ESCALATED;
        if (record.getOutcome() == Outcome.STOPPED_CORRECTLY) return LifecycleState.STOPPED;
        if ((policy.getActionTaken() == ActionTaken.RETRY_PAYMENT
                || policy.getActionTaken() == ActionTaken.SCHEDULE_MANDATE_RETRY)
                && record.getAttemptNumber() < policy.getMaxAttemptsAllowed()) return LifecycleState.RETRY_SCHEDULED;
        if (policy.getActionTaken() == ActionTaken.RETRY_PAYMENT
                || policy.getActionTaken() == ActionTaken.SCHEDULE_MANDATE_RETRY) return LifecycleState.RETRY_EXHAUSTED;
        return LifecycleState.NOT_RECOVERED;
    }

    private int value(Integer value) { return value == null ? 0 : value; }
}
