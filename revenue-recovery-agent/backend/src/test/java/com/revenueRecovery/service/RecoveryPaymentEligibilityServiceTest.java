package com.revenueRecovery.service;

import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryPaymentEligibilityServiceTest {
    private final RecoveryPaymentLinkRepository links = mock(RecoveryPaymentLinkRepository.class);
    private final PolicyEngine policyEngine = new PolicyEngine();
    private final RecoveryPaymentEligibilityService service =
            new RecoveryPaymentEligibilityService(policyEngine, links);

    @Test
    void onlyPersistedLinkCompatiblePoliciesAreEligible() {
        assertDoesNotThrow(() -> service.requireEligibleForNewLink(
                recoveryCase(RootCause.CHECKOUT_ABANDONED, Outcome.NOT_RECOVERED)));
        assertDoesNotThrow(() -> service.requireEligibleForNewLink(
                recoveryCase(RootCause.INSUFFICIENT_BALANCE, Outcome.NOT_RECOVERED)));

        List<RootCause> blocked = List.of(
                RootCause.USER_CANCELLED,
                RootCause.INCORRECT_PIN,
                RootCause.PAYMENT_PENDING,
                RootCause.WEAK_NETWORK,
                RootCause.MERCHANT_GATEWAY_ISSUE,
                RootCause.MANDATE_EXPIRED,
                RootCause.UNKNOWN);
        for (RootCause cause : blocked) {
            RecoveryPaymentException exception = assertThrows(RecoveryPaymentException.class,
                    () -> service.requireEligibleForNewLink(recoveryCase(cause, Outcome.NOT_RECOVERED)));
            assertEquals("Recovery payment is not allowed for this policy outcome.", exception.getMessage());
        }
    }

    @Test
    void recoveredEscalatedAndDuplicateLinkedCasesAreRejected() {
        assertEquals("This case is already recovered.", assertThrows(RecoveryPaymentException.class,
                () -> service.requireEligibleForNewLink(
                        recoveryCase(RootCause.CHECKOUT_ABANDONED, Outcome.RECOVERED))).getMessage());
        assertEquals("Recovery payment is not allowed for this policy outcome.",
                assertThrows(RecoveryPaymentException.class,
                        () -> service.requireEligibleForNewLink(
                                recoveryCase(RootCause.CHECKOUT_ABANDONED, Outcome.ESCALATED))).getMessage());

        RecoveryDemoCase eligible = recoveryCase(RootCause.CHECKOUT_ABANDONED, Outcome.NOT_RECOVERED);
        when(links.existsByRecoveryCaseEventId(eligible.getEventId())).thenReturn(true);
        assertEquals("A recovery payment link already exists for this case.",
                assertThrows(RecoveryPaymentException.class,
                        () -> service.requireEligibleForNewLink(eligible)).getMessage());
    }

    private RecoveryDemoCase recoveryCase(RootCause cause, Outcome outcome) {
        RecoveryDemoCase recoveryCase = new RecoveryDemoCase();
        recoveryCase.setEventId("TEST_" + cause.name());
        recoveryCase.setRootCause(cause);
        recoveryCase.setPolicyAction(policyEngine.decide(cause).getActionTaken());
        recoveryCase.setOutcome(outcome);
        recoveryCase.setRecoveryStatus(outcome == Outcome.RECOVERED ? "recovered" : "awaiting_customer_payment");
        recoveryCase.setAmount(new BigDecimal("500.00"));
        recoveryCase.setCurrency("INR");
        return recoveryCase;
    }
}
