package com.revenueRecovery.service;

import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.LifecycleState;

import java.time.Instant;

public class RecoveryActionBlockedException extends RuntimeException {
    private final String eventId;
    private final LifecycleState currentState;
    private final ActionTaken requestedAction;
    private final Instant nextEligibleActionAt;

    public RecoveryActionBlockedException(String eventId, LifecycleState currentState,
            ActionTaken requestedAction, String reason, Instant nextEligibleActionAt) {
        super(reason);
        this.eventId = eventId;
        this.currentState = currentState;
        this.requestedAction = requestedAction;
        this.nextEligibleActionAt = nextEligibleActionAt;
    }

    public String getEventId() { return eventId; }
    public LifecycleState getCurrentState() { return currentState; }
    public ActionTaken getRequestedAction() { return requestedAction; }
    public Instant getNextEligibleActionAt() { return nextEligibleActionAt; }
}
