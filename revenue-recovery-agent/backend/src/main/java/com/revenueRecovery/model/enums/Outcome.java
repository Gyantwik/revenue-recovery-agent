package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Outcome {
    RECOVERED,
    NOT_RECOVERED,
    ESCALATED,
    STOPPED_CORRECTLY;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Outcome fromJson(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
