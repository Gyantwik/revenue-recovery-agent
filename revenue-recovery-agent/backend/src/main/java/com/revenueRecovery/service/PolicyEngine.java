package com.revenueRecovery.service;

import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.RootCause;
import org.springframework.stereotype.Service;

import java.util.EnumMap;

@Service
public class PolicyEngine {

    private static final EnumMap<RootCause, PolicyDecision> POLICIES = createPolicies();

    public PolicyDecision decide(RootCause cause) {
        return POLICIES.getOrDefault(cause, POLICIES.get(RootCause.UNKNOWN));
    }

    private static EnumMap<RootCause, PolicyDecision> createPolicies() {
        EnumMap<RootCause, PolicyDecision> policies = new EnumMap<>(RootCause.class);

        policies.put(RootCause.WEAK_NETWORK, decision(
                RootCause.WEAK_NETWORK,
                ActionTaken.RETRY_PAYMENT,
                2,
                "Weak network → Retry, max 2"));
        policies.put(RootCause.BANK_TEMP_ERROR, decision(
                RootCause.BANK_TEMP_ERROR,
                ActionTaken.RETRY_PAYMENT,
                2,
                "Bank temp error → Retry, max 2, 30 min window"));
        policies.put(RootCause.PAYMENT_PENDING, decision(
                RootCause.PAYMENT_PENDING,
                ActionTaken.VERIFY_STATUS,
                1,
                "Payment pending → Verify status only"));
        policies.put(RootCause.INSUFFICIENT_BALANCE, decision(
                RootCause.INSUFFICIENT_BALANCE,
                ActionTaken.SEND_ALT_PAYMENT_LINK,
                1,
                "Insufficient balance → Send alternative payment link"));
        policies.put(RootCause.USER_CANCELLED, decision(
                RootCause.USER_CANCELLED,
                ActionTaken.NO_ACTION_STOP,
                0,
                "User cancellation -> Stop"));
        policies.put(RootCause.INCORRECT_PIN, decision(
                RootCause.INCORRECT_PIN,
                ActionTaken.NO_ACTION_STOP,
                0,
                "Auth failure (PIN) -> Stop"));
        policies.put(RootCause.CHECKOUT_ABANDONED, decision(
                RootCause.CHECKOUT_ABANDONED,
                ActionTaken.SEND_RECOVERY_LINK,
                1,
                "Checkout abandoned → Send recovery link"));
        policies.put(RootCause.MERCHANT_GATEWAY_ISSUE, decision(
                RootCause.MERCHANT_GATEWAY_ISSUE,
                ActionTaken.ESCALATE_MERCHANT,
                0,
                "Gateway issue -> Escalate"));
        policies.put(RootCause.MANDATE_FAILED_RETRYABLE, decision(
                RootCause.MANDATE_FAILED_RETRYABLE,
                ActionTaken.SCHEDULE_MANDATE_RETRY,
                2,
                "Mandate failed retryable → Retry, max 2"));
        policies.put(RootCause.MANDATE_EXPIRED, decision(
                RootCause.MANDATE_EXPIRED,
                ActionTaken.ESCALATE_MERCHANT,
                0,
                "Mandate expired -> Escalate"));
        policies.put(RootCause.UNKNOWN, decision(
                RootCause.UNKNOWN,
                ActionTaken.ESCALATE_MERCHANT,
                0,
                "Low confidence -> Manual Review"));

        return policies;
    }

    private static PolicyDecision decision(
            RootCause rootCause,
            ActionTaken actionTaken,
            int maxAttemptsAllowed,
            String policyRuleMatched) {
        return new PolicyDecision(rootCause, actionTaken, maxAttemptsAllowed, policyRuleMatched);
    }
}
