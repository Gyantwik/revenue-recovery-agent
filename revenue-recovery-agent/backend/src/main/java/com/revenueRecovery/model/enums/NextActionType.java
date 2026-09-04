package com.revenueRecovery.model.enums;

/** The only executable financial action is the dedicated Test Mode demo checkout. */
public enum NextActionType {
    NONE,
    DISPLAY_INFORMATION,
    OPEN_RECOVERY_CHECKOUT,
    RESUME_RECOVERY_CHECKOUT,
    CHECK_PAYMENT_STATUS,
    OPEN_TEST_MODE_RECOVERY_CHECKOUT,
    CREATE_RESERVATION,
    SIMULATE_RECONNECT
}
