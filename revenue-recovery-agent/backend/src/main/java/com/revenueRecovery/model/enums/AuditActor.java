package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum AuditActor {
    SYSTEM_SIMULATION, MERCHANT_MANUAL;

    @JsonValue
    public String toJson() { return name().toLowerCase(Locale.ROOT); }
}
