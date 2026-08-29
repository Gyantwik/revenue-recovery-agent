package com.revenueRecovery.service;

import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.RootCause;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyEngineTest {

    private final PolicyEngine policyEngine = new PolicyEngine();

    @Test
    void resolvesAllPolicyRowsToTheCorrectActionAndMaxAttempts() {
        Map<RootCause, ExpectedPolicy> expectedPolicies = new EnumMap<>(RootCause.class);
        expectedPolicies.put(RootCause.WEAK_NETWORK, new ExpectedPolicy(ActionTaken.RETRY_PAYMENT, 2));
        expectedPolicies.put(RootCause.BANK_TEMP_ERROR, new ExpectedPolicy(ActionTaken.RETRY_PAYMENT, 2));
        expectedPolicies.put(RootCause.PAYMENT_PENDING, new ExpectedPolicy(ActionTaken.VERIFY_STATUS, 1));
        expectedPolicies.put(RootCause.INSUFFICIENT_BALANCE, new ExpectedPolicy(ActionTaken.SEND_ALT_PAYMENT_LINK, 1));
        expectedPolicies.put(RootCause.USER_CANCELLED, new ExpectedPolicy(ActionTaken.NO_ACTION_STOP, 0));
        expectedPolicies.put(RootCause.INCORRECT_PIN, new ExpectedPolicy(ActionTaken.NO_ACTION_STOP, 0));
        expectedPolicies.put(RootCause.CHECKOUT_ABANDONED, new ExpectedPolicy(ActionTaken.SEND_RECOVERY_LINK, 1));
        expectedPolicies.put(RootCause.MERCHANT_GATEWAY_ISSUE, new ExpectedPolicy(ActionTaken.ESCALATE_MERCHANT, 0));
        expectedPolicies.put(RootCause.MANDATE_FAILED_RETRYABLE, new ExpectedPolicy(ActionTaken.SCHEDULE_MANDATE_RETRY, 2));
        expectedPolicies.put(RootCause.MANDATE_EXPIRED, new ExpectedPolicy(ActionTaken.ESCALATE_MERCHANT, 0));
        expectedPolicies.put(RootCause.UNKNOWN, new ExpectedPolicy(ActionTaken.ESCALATE_MERCHANT, 0));

        assertEquals(11, expectedPolicies.size());
        expectedPolicies.forEach((rootCause, expected) -> {
            PolicyDecision actual = policyEngine.decide(rootCause);
            assertEquals(rootCause, actual.getRootCause(), rootCause.name());
            assertEquals(expected.actionTaken(), actual.getActionTaken(), rootCause.name());
            assertEquals(expected.maxAttempts(), actual.getMaxAttemptsAllowed(), rootCause.name());
        });
    }

    private record ExpectedPolicy(ActionTaken actionTaken, int maxAttempts) {
    }
}
