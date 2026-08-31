package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.enums.NextActionMode;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.model.enums.NextRecoveryAction;
import com.revenueRecovery.model.enums.Outcome;

public record NextRecoveryActionResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("current_outcome") Outcome currentOutcome,
        @JsonProperty("lifecycle_state") String lifecycleState,
        @JsonProperty("attempts_made") int attemptsMade,
        @JsonProperty("max_attempts") int maxAttempts,
        @JsonProperty("is_action_allowed") boolean actionAllowed,
        @JsonProperty("recommended_action") NextRecoveryAction recommendedAction,
        @JsonProperty("button_label") String buttonLabel,
        String title,
        String reason,
        @JsonProperty("next_step") String nextStep,
        @JsonProperty("risk_note") String riskNote,
        @JsonProperty("action_type") NextActionType actionType,
        NextActionMode mode) {
}
