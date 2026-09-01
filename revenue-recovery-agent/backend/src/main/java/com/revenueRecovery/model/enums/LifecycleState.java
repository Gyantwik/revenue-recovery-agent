package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum LifecycleState {
    RECEIVED, AT_RISK, CLASSIFIED, ACTION_APPROVED, RETRY_SCHEDULED,
    VERIFYING_PAYMENT, RECOVERY_LINK_SENT, RETRY_EXHAUSTED,
    RECOVERED, RECOVERED_BY_VERIFIED_TEST_PAYMENT, NOT_RECOVERED, STOPPED, ESCALATED;

    public boolean isTerminal() {
        return this == RECOVERED || this == RECOVERED_BY_VERIFIED_TEST_PAYMENT
                || this == NOT_RECOVERED || this == STOPPED
                || this == ESCALATED || this == RETRY_EXHAUSTED;
    }

    @JsonValue
    public String toJson() { return name().toLowerCase(Locale.ROOT); }

    @JsonCreator
    public static LifecycleState fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
