package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.LifecycleState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class RecoveryStateMachine {
    private static final Map<LifecycleState, Set<LifecycleState>> TRANSITIONS = transitions();

    public void validateTransition(LifecycleState previous, LifecycleState next) {
        if (previous == null || next == null || !TRANSITIONS.getOrDefault(previous, Set.of()).contains(next)) {
            throw new IllegalStateException("Blocked lifecycle transition: " + previous + " -> " + next);
        }
        if (previous.isTerminal()) {
            throw new IllegalStateException("Terminal lifecycle state cannot transition: " + previous);
        }
    }

    public void validateAction(AuditRecord record, PolicyDecision policy, ActionTaken requested,
            int requestedAttempt, Instant now, boolean duplicate) {
        LifecycleState state = record.getLifecycleState();
        if (state == null || state.isTerminal()) {
            blocked(record, requested, "Action blocked: transaction is already terminal", null);
        }
        if (policy.getActionTaken() != requested) {
            blocked(record, requested, "Action blocked by deterministic policy; allowed action is "
                    + policy.getActionTaken().toJson(), record.getNextEligibleActionAt());
        }
        if (duplicate) {
            blocked(record, requested, "Duplicate action request blocked by idempotency key", record.getNextEligibleActionAt());
        }
        if (requestedAttempt > policy.getMaxAttemptsAllowed() && policy.getMaxAttemptsAllowed() >= 0) {
            blocked(record, requested, "Maximum action attempts exhausted", record.getNextEligibleActionAt());
        }
        if ((requested == ActionTaken.RETRY_PAYMENT || requested == ActionTaken.SCHEDULE_MANDATE_RETRY)
                && record.getRecoveryWindowExpiresAt() != null && now.isAfter(record.getRecoveryWindowExpiresAt())) {
            blocked(record, requested, "Recovery window expired; retry is no longer permitted", null);
        }
        if (record.getNextEligibleActionAt() != null && now.isBefore(record.getNextEligibleActionAt())) {
            blocked(record, requested, "Action is not eligible yet", record.getNextEligibleActionAt());
        }
        boolean retryState = state == LifecycleState.ACTION_APPROVED || state == LifecycleState.RETRY_SCHEDULED;
        if ((requested == ActionTaken.RETRY_PAYMENT || requested == ActionTaken.SCHEDULE_MANDATE_RETRY) && !retryState) {
            blocked(record, requested, "Retry is not allowed from current lifecycle state", record.getNextEligibleActionAt());
        }
        if (requested != ActionTaken.RETRY_PAYMENT && requested != ActionTaken.SCHEDULE_MANDATE_RETRY
                && state != LifecycleState.ACTION_APPROVED) {
            blocked(record, requested, "Action is not allowed from current lifecycle state", record.getNextEligibleActionAt());
        }
    }

    private void blocked(AuditRecord record, ActionTaken requested, String reason, Instant next) {
        throw new RecoveryActionBlockedException(record.getEventId(), record.getLifecycleState(), requested, reason, next);
    }

    private static Map<LifecycleState, Set<LifecycleState>> transitions() {
        EnumMap<LifecycleState, Set<LifecycleState>> map = new EnumMap<>(LifecycleState.class);
        map.put(LifecycleState.RECEIVED, EnumSet.of(LifecycleState.AT_RISK, LifecycleState.STOPPED, LifecycleState.ESCALATED));
        map.put(LifecycleState.AT_RISK, EnumSet.of(LifecycleState.CLASSIFIED));
        map.put(LifecycleState.CLASSIFIED, EnumSet.of(LifecycleState.ACTION_APPROVED, LifecycleState.STOPPED, LifecycleState.ESCALATED));
        map.put(LifecycleState.ACTION_APPROVED, EnumSet.of(LifecycleState.RETRY_SCHEDULED,
                LifecycleState.VERIFYING_PAYMENT, LifecycleState.RECOVERY_LINK_SENT,
                LifecycleState.STOPPED, LifecycleState.ESCALATED, LifecycleState.RETRY_EXHAUSTED));
        map.put(LifecycleState.RETRY_SCHEDULED, EnumSet.of(LifecycleState.RECOVERED,
                LifecycleState.NOT_RECOVERED, LifecycleState.RETRY_EXHAUSTED));
        map.put(LifecycleState.VERIFYING_PAYMENT, EnumSet.of(LifecycleState.RECOVERED, LifecycleState.ESCALATED));
        map.put(LifecycleState.RECOVERY_LINK_SENT, EnumSet.of(LifecycleState.RECOVERED,
                LifecycleState.NOT_RECOVERED, LifecycleState.ESCALATED));
        return Map.copyOf(map);
    }
}
