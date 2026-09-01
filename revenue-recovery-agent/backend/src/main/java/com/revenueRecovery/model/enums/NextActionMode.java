package com.revenueRecovery.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NextActionMode {
    SYNTHETIC_BENCHMARK("synthetic_benchmark"),
    RAZORPAY_TEST_RECOVERY("razorpay_test_recovery"),
    RAZORPAY_TEST_DEMO("razorpay_test_demo");

    private final String jsonValue;

    NextActionMode(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String toJson() {
        return jsonValue;
    }
}
