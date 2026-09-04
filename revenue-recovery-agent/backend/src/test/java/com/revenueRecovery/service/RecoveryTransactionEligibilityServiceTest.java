package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RecoveryCheckoutAction;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryTransactionEligibilityServiceTest {
    private final TransactionRecoveryPaymentLinkRepository links =
            mock(TransactionRecoveryPaymentLinkRepository.class);
    private final RecoveryTransactionEligibilityService service =
            new RecoveryTransactionEligibilityService(links);

    @BeforeEach
    void noExistingLink() {
        when(links.findByEventEventId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void safeCustomerInitiatedCategoriesHaveExactActions() {
        assertAllowed(RootCause.CHECKOUT_ABANDONED, Outcome.NOT_RECOVERED, 1, 1,
                RecoveryCheckoutAction.RESUME_PAYMENT, "Resume Payment");
        assertAllowed(RootCause.MANDATE_FAILED_RETRYABLE, Outcome.NOT_RECOVERED, 2, 2,
                RecoveryCheckoutAction.PAY_MANUALLY, "Pay Manually");
        assertBlocked(record(RootCause.INSUFFICIENT_BALANCE, Outcome.NOT_RECOVERED, 1, 1),
                "Alternative Payment Link Sent");
        assertBlocked(record(RootCause.INCORRECT_PIN, Outcome.STOPPED_CORRECTLY, 0, 0),
                "No Recovery Payment");
        assertBlocked(record(RootCause.BANK_TEMP_ERROR, Outcome.NOT_RECOVERED, 2, 2),
                "Retry Limit Reached");
    }

    @Test
    void scheduledRetriesCannotCreateDuplicateCheckout() {
        assertBlocked(record(RootCause.BANK_TEMP_ERROR, Outcome.NOT_RECOVERED, 1, 2), "Retry Scheduled");
        assertBlocked(record(RootCause.MANDATE_FAILED_RETRYABLE, Outcome.NOT_RECOVERED, 1, 2),
                "Mandate Retry Scheduled");
    }

    @Test
    void uncertainProtectedAndReviewCategoriesAreBlocked() {
        var pending = service.evaluate(record(RootCause.PAYMENT_PENDING, Outcome.NOT_RECOVERED, 1, 1));
        assertTrue(pending.allowed());
        assertEquals("Verify Status", pending.buttonLabel());
        assertBlocked(record(RootCause.WEAK_NETWORK, Outcome.NOT_RECOVERED, 2, 2),
                "Verify Previous Payment First");
        assertBlocked(record(RootCause.USER_CANCELLED, Outcome.STOPPED_CORRECTLY, 0, 0),
                "No Automatic Recovery Allowed");
        assertBlocked(record(RootCause.MERCHANT_GATEWAY_ISSUE, Outcome.ESCALATED, 0, 0),
                "Escalated for Review");
        assertBlocked(record(RootCause.UNKNOWN, Outcome.ESCALATED, 0, 0), "Escalated for Review");
        assertBlocked(record(RootCause.MANDATE_EXPIRED, Outcome.ESCALATED, 0, 0), "Escalated for Review");
    }

    @Test
    void recoveredAndEscalatedEligibleCausesAreBlocked() {
        assertBlocked(record(RootCause.CHECKOUT_ABANDONED, Outcome.RECOVERED, 1, 1), "Already Recovered");
        assertBlocked(record(RootCause.CHECKOUT_ABANDONED, Outcome.ESCALATED, 1, 1),
                "Escalated for Review");
    }

    private void assertAllowed(RootCause cause, Outcome outcome, int attempts, int maximum,
            RecoveryCheckoutAction action, String label) {
        var result = service.evaluate(record(cause, outcome, attempts, maximum));
        assertTrue(result.allowed());
        assertEquals(action, result.recoveryAction());
        assertEquals(label, result.buttonLabel());
    }

    private void assertBlocked(AuditRecord record, String label) {
        var result = service.evaluate(record);
        assertFalse(result.allowed());
        assertEquals(label, result.buttonLabel());
    }

    private AuditRecord record(RootCause cause, Outcome outcome, int attempts, int maximum) {
        AuditRecord record = new AuditRecord();
        record.setEventId("event-" + cause + "-" + attempts);
        record.setRootCause(cause);
        record.setOutcome(outcome);
        record.setAttemptNumber(attempts);
        record.setMaxAttemptsAllowed(maximum);
        record.setLifecycleState(attempts >= maximum && maximum > 0
                ? LifecycleState.RETRY_EXHAUSTED : LifecycleState.RETRY_SCHEDULED);
        return record;
    }
}
