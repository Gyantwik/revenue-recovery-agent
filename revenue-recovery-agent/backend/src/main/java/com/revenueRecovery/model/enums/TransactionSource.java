package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TransactionSource {
    LIVE, SEEDED_REFERENCE;

    @JsonValue
    public String toJson() { return name().toLowerCase(Locale.ROOT); }

    @JsonCreator
    public static TransactionSource fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
