package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum RootCause {
    BANK_TEMP_ERROR,
    WEAK_NETWORK,
    PAYMENT_PENDING,
    INSUFFICIENT_BALANCE,
    USER_CANCELLED,
    INCORRECT_PIN,
    MERCHANT_GATEWAY_ISSUE,
    CHECKOUT_ABANDONED,
    MANDATE_FAILED_RETRYABLE,
    MANDATE_EXPIRED,
    UNKNOWN;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static RootCause fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
