package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum VerificationResult {
    CONFIRMED_SUCCESS, STILL_PENDING, CONFIRMED_FAILED_RETRY_BLOCKED;

    @JsonValue
    public String toJson() { return name().toLowerCase(Locale.ROOT); }

    @JsonCreator
    public static VerificationResult fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
