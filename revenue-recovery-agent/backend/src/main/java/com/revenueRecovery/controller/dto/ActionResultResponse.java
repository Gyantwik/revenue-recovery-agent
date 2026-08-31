package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.LifecycleState;

import java.time.Instant;

public record ActionResultResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("current_state") LifecycleState currentState,
        @JsonProperty("requested_action") ActionTaken requestedAction,
        String reason,
        @JsonProperty("next_eligible_action_at") Instant nextEligibleActionAt) {
}
