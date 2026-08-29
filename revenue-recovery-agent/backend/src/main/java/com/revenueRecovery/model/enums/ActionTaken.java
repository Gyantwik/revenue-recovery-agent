package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum ActionTaken {
    RETRY_PAYMENT,
    VERIFY_STATUS,
    SEND_ALT_PAYMENT_LINK,
    SEND_RECOVERY_LINK,
    NO_ACTION_STOP,
    ESCALATE_MERCHANT,
    SCHEDULE_MANDATE_RETRY;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ActionTaken fromJson(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
