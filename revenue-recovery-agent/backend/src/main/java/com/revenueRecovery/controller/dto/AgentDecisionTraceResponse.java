package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.AgentDecisionTrace;
import com.revenueRecovery.model.enums.AgentTraceStage;
import com.revenueRecovery.model.enums.AuditActor;

import java.time.Instant;

public record AgentDecisionTraceResponse(
        @JsonProperty("event_id") String eventId,
        AgentTraceStage stage,
        String summary,
        String detail,
        AuditActor actor,
        Instant timestamp) {
    public static AgentDecisionTraceResponse from(AgentDecisionTrace trace) {
        return new AgentDecisionTraceResponse(trace.getEventId(), trace.getStage(), trace.getSummary(),
                trace.getDetail(), trace.getActor(), trace.getTimestamp());
    }
}
