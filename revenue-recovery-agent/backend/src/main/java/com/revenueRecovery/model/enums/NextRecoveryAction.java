package com.revenueRecovery.model.enums;

/** Stable machine-readable recommendations returned by the Phase 5 decision API. */
public enum NextRecoveryAction {
    ALREADY_RECOVERED,
    STOPPED_BY_POLICY,
    ESCALATE_TO_MERCHANT,
    ESCALATE_MANDATE_RENEWAL,
    VERIFY_PAYMENT_STATUS,
    AWAIT_SCHEDULED_RETRY,
    AWAIT_SCHEDULED_MANDATE_RETRY,
    ESCALATE_AFTER_RETRY_EXHAUSTED,
    SEND_RECOVERY_LINK,
    SEND_ALT_PAYMENT_LINK
}
