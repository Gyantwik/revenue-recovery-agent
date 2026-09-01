package com.revenueRecovery.model.enums;

/** Stable customer-initiated actions. None of these represents an automatic debit. */
public enum RecoveryCheckoutAction {
    RESUME_PAYMENT,
    CHOOSE_ANOTHER_PAYMENT_METHOD,
    TRY_PAYMENT_AGAIN_SECURELY,
    TRY_PAYMENT_AGAIN,
    PAY_MANUALLY
}
