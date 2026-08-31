package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.ClassificationResult;
import com.revenueRecovery.model.DetectionResult;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.time.Duration;

@Service
public class RevenueRecoveryPipeline {

    private static final double CONFIDENCE_THRESHOLD = 0.75;

    private final EventRepository eventRepository;
    private final DetectionService detectionService;
    private final ClassificationService classificationService;
    private final PolicyEngine policyEngine;
    private final ActionExecutor actionExecutor;
    private final AuditService auditService;
    private final AuditRecordRepository auditRecordRepository;
    private final RecoveryStateMachine stateMachine;
    private final AuditHistoryService historyService;

    public RevenueRecoveryPipeline(
            EventRepository eventRepository,
            DetectionService detectionService,
            ClassificationService classificationService,
            PolicyEngine policyEngine,
            ActionExecutor actionExecutor,
            AuditService auditService,
            AuditRecordRepository auditRecordRepository,
            RecoveryStateMachine stateMachine,
            AuditHistoryService historyService) {
        this.eventRepository = eventRepository;
        this.detectionService = detectionService;
        this.classificationService = classificationService;
        this.policyEngine = policyEngine;
        this.actionExecutor = actionExecutor;
        this.auditService = auditService;
        this.auditRecordRepository = auditRecordRepository;
        this.stateMachine = stateMachine;
        this.historyService = historyService;
    }

    @Transactional
    public AuditRecord processEvent(Event event) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(event.getEventId(), "eventId must not be null");
        AuditRecord existing = auditRecordRepository.findByEventId(event.getEventId()).orElse(null);
        if (existing != null) {
            return auditService.trackOutcome(ensureLifecycle(existing));
        }
        Event normalizedEvent = eventRepository.findByEventId(event.getEventId())
                .orElseGet(() -> eventRepository.save(event));

        DetectionResult detection = detectionService.detect(normalizedEvent);
        ClassificationResult classification = classificationService.classify(normalizedEvent);

        RootCause effectiveRootCause = classification.getConfidence() < CONFIDENCE_THRESHOLD
                ? RootCause.UNKNOWN
                : classification.getRootCause();
        PolicyDecision policy = policyEngine.decide(effectiveRootCause);

        AuditRecord execution = actionExecutor.execute(normalizedEvent, policy);
        AuditRecord savedAudit = auditService.logDecision(
                normalizedEvent, detection, classification, policy, execution);

        if (policy.getActionTaken() == ActionTaken.RETRY_PAYMENT) {
            savedAudit.setRecoveryWindowExpiresAt(normalizedEvent.getTimestamp().plus(Duration.ofMinutes(30)));
            savedAudit.setNextEligibleActionAt(normalizedEvent.getTimestamp());
        }
        LifecycleState intermediate = intermediateState(policy.getActionTaken());
        LifecycleState terminal = terminalState(savedAudit, policy);
        savedAudit.setLifecycleState(terminal);
        savedAudit = auditRecordRepository.save(savedAudit);
        appendInitialHistory(savedAudit, intermediate, terminal);
        return auditService.trackOutcome(savedAudit);
    }

    private AuditRecord ensureLifecycle(AuditRecord record) {
        // Phase 2 represented a failed pending-payment verification as NOT_RECOVERED.
        // Phase 3 permits only RECOVERED or ESCALATED after VERIFYING_PAYMENT.
        if (record.getActionTaken() == ActionTaken.VERIFY_STATUS
                && record.getOutcome() == Outcome.NOT_RECOVERED) {
            record.setOutcome(Outcome.ESCALATED);
            record.setStopOrEscalateReason(
                    "Original payment status must be verified to avoid a duplicate debit");
        }
        LifecycleState intermediate = intermediateState(record.getActionTaken());
        LifecycleState terminal = terminalState(record, policyEngine.decide(record.getRootCause()));
        if (record.getLifecycleState() == null) {
            record.setLifecycleState(terminal);
        }
        if (record.getActionTaken() == ActionTaken.RETRY_PAYMENT && record.getTimestamp() != null) {
            if (record.getRecoveryWindowExpiresAt() == null) {
                record.setRecoveryWindowExpiresAt(record.getTimestamp().plus(Duration.ofMinutes(30)));
            }
            if (record.getNextEligibleActionAt() == null) record.setNextEligibleActionAt(record.getTimestamp());
        }
        record = auditRecordRepository.save(record);
        if (!historyService.hasHistory(record.getEventId())) appendInitialHistory(record, intermediate, terminal);
        return record;
    }

    private LifecycleState intermediateState(ActionTaken action) {
        return switch (action) {
            case RETRY_PAYMENT, SCHEDULE_MANDATE_RETRY -> LifecycleState.RETRY_SCHEDULED;
            case VERIFY_STATUS -> LifecycleState.VERIFYING_PAYMENT;
            case SEND_ALT_PAYMENT_LINK, SEND_RECOVERY_LINK -> LifecycleState.RECOVERY_LINK_SENT;
            case NO_ACTION_STOP -> LifecycleState.STOPPED;
            case ESCALATE_MERCHANT -> LifecycleState.ESCALATED;
        };
    }

    private LifecycleState terminalState(AuditRecord record, PolicyDecision policy) {
        if (record.getOutcome() == Outcome.RECOVERED) return LifecycleState.RECOVERED;
        if (record.getOutcome() == Outcome.STOPPED_CORRECTLY) return LifecycleState.STOPPED;
        if (record.getOutcome() == Outcome.ESCALATED) return LifecycleState.ESCALATED;
        if ((policy.getActionTaken() == ActionTaken.RETRY_PAYMENT
                || policy.getActionTaken() == ActionTaken.SCHEDULE_MANDATE_RETRY)
                && record.getAttemptNumber() >= policy.getMaxAttemptsAllowed()) {
            return LifecycleState.RETRY_EXHAUSTED;
        }
        return LifecycleState.NOT_RECOVERED;
    }

    private void appendInitialHistory(AuditRecord record, LifecycleState intermediate, LifecycleState terminal) {
        transition(record, null, LifecycleState.RECEIVED, null, null, "Synthetic event received", "received");
        transition(record, LifecycleState.RECEIVED, LifecycleState.AT_RISK, null, null, "Synthetic event detected as at risk", "at-risk");
        transition(record, LifecycleState.AT_RISK, LifecycleState.CLASSIFIED, null, null, "Rule-based classification completed", "classified");
        transition(record, LifecycleState.CLASSIFIED, LifecycleState.ACTION_APPROVED, null, null, "Deterministic policy approved action", "approved");
        transition(record, LifecycleState.ACTION_APPROVED, intermediate, record.getActionTaken(),
                intermediate.isTerminal() ? record.getOutcome() : null,
                actionReason(record), "action");
        if (!intermediate.isTerminal()) {
            transition(record, intermediate, terminal, null, record.getOutcome(),
                    "Synthetic/test-mode outcome recorded", "terminal");
        }
    }

    private String actionReason(AuditRecord record) {
        return switch (record.getRootCause()) {
            case USER_CANCELLED -> "Customer explicitly cancelled; policy blocks all later automatic actions";
            case INCORRECT_PIN -> "Authentication failure requires the customer to initiate a fresh payment";
            case PAYMENT_PENDING -> "Original payment verification required to avoid a duplicate debit";
            case BANK_TEMP_ERROR -> "Synthetic/test-mode retry approved inside the 30-minute recovery window";
            case WEAK_NETWORK -> "Synthetic/test-mode bounded retry; network signal is not payment confirmation";
            case MANDATE_FAILED_RETRYABLE -> "Synthetic/test-mode mandate retry; no real bank or NPCI execution";
            case INSUFFICIENT_BALANCE -> "Synthetic/test-mode alternative payment-link simulation; no customer message sent";
            case CHECKOUT_ABANDONED -> "Synthetic/test-mode recovery-link simulation; no customer message sent";
            case MERCHANT_GATEWAY_ISSUE -> "Merchant/gateway-side issue requires manual review; automatic retry blocked";
            case MANDATE_EXPIRED -> "Fresh customer mandate/consent required; automatic retry blocked";
            case UNKNOWN -> "Low classification confidence; manual review required";
        };
    }

    private void transition(AuditRecord record, LifecycleState previous, LifecycleState next,
            ActionTaken action, Outcome outcome, String reason, String suffix) {
        if (previous != null) stateMachine.validateTransition(previous, next);
        historyService.append(record, previous, next, action, outcome, reason,
                AuditActor.SYSTEM_SIMULATION, record.getEventId() + ":initial:" + suffix);
    }
}
