package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AgentTraceStage {
    OBSERVE, CLASSIFY, DECIDE, GUARDRAIL_CHECK, ACT;

    @JsonValue
    public String toJson() { return name().toLowerCase(Locale.ROOT); }
}
