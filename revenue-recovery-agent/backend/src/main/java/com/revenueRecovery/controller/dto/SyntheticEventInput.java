package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.model.enums.Outcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyntheticEventInput(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_type") String caseType,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        @JsonProperty("signals_used") List<String> signalsUsed,
        @JsonProperty("root_cause") RootCause rootCause,
        @JsonProperty("attempt_number") Integer attemptNumber,
        @JsonProperty("max_attempts_allowed") Integer maxAttemptsAllowed,
        Outcome outcome,
        @JsonProperty("recovered_amount") BigDecimal recoveredAmount) {

    public Event toEvent() {
        Event event = new Event();
        event.setEventId(eventId);
        event.setCaseType(caseType);
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setTimestamp(timestamp);
        event.setSignalsUsed(signalsUsed);
        return event;
    }
}
