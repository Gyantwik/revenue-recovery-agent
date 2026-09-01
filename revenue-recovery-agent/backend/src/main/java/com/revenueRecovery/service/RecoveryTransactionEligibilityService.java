package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.NextRecoveryAction;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RecoveryCheckoutAction;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.springframework.stereotype.Service;

@Service
public class RecoveryTransactionEligibilityService {
    private final TransactionRecoveryPaymentLinkRepository linkRepository;

    public RecoveryTransactionEligibilityService(TransactionRecoveryPaymentLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    public RecoveryCheckoutDecision evaluate(AuditRecord record) {
        RecoveryCheckoutDecision policy = evaluatePolicy(record);
        if (!policy.allowed()) return policy;

        TransactionRecoveryPaymentLink link = linkRepository.findByEventEventId(record.getEventId()).orElse(null);
        if (link == null) return policy;
        if (RecoveryPaymentFinalizationService.LINK_RECOVERED.equals(link.getStatus())) {
            return blocked(NextRecoveryAction.ALREADY_RECOVERED, "Already Recovered",
                    "Recovery payment is complete",
                    "A verified Test Mode payment already recovered this transaction.",
                    "Review the persisted outcome and append-only audit history.",
                    "A second payment order would risk duplicate collection.");
        }
        return blocked(NextRecoveryAction.CUSTOMER_RECOVERY_CHECKOUT, "Recovery Payment Pending",
                "A recovery payment is already in progress",
                "This transaction already has a linked Test Mode recovery order.",
                "Complete or review the existing linked payment attempt.",
                "Only one active or verified recovery link is allowed per transaction.");
    }

    public RecoveryCheckoutDecision requireEligibleForNewLink(AuditRecord record) {
        RecoveryCheckoutDecision decision = evaluate(record);
        if (!decision.allowed()) throw RecoveryPaymentException.notAllowed(decision.reason());
        return decision;
    }

    public RecoveryCheckoutDecision requireEligibleForFinalization(AuditRecord record) {
        RecoveryCheckoutDecision decision = evaluatePolicy(record);
        if (!decision.allowed()) throw RecoveryPaymentException.notAllowed(decision.reason());
        return decision;
    }

    private RecoveryCheckoutDecision evaluatePolicy(AuditRecord record) {
        if (record == null) {
            return blocked(NextRecoveryAction.STOPPED_BY_POLICY, "Transaction Not Found",
                    "Transaction is unavailable", "The persisted transaction could not be loaded.",
                    "Refresh and try again.", "No payment order was created.");
        }
        if (record.getOutcome() == Outcome.RECOVERED) {
            return blocked(NextRecoveryAction.ALREADY_RECOVERED, "Already Recovered",
                    "Payment recovery is complete", "This transaction is already recovered.",
                    "Review the existing outcome and audit history.",
                    "Do not initiate another payment.");
        }
        return switch (record.getRootCause()) {
            case CHECKOUT_ABANDONED -> eligibleUnlessEscalated(record,
                    allowed(RecoveryCheckoutAction.RESUME_PAYMENT, "Resume Payment",
                    "Customer payment can be resumed",
                    "Checkout was abandoned before payment completion."));
            case INSUFFICIENT_BALANCE -> eligibleUnlessEscalated(record,
                    allowed(RecoveryCheckoutAction.CHOOSE_ANOTHER_PAYMENT_METHOD,
                    "Choose Another Payment Method", "A voluntary alternative payment can be attempted",
                    "The customer may add funds or choose another payment method."));
            case INCORRECT_PIN -> eligibleUnlessEscalated(record,
                    allowed(RecoveryCheckoutAction.TRY_PAYMENT_AGAIN_SECURELY,
                    "Try Payment Again Securely", "Customer-initiated secure retry is available",
                    "Automatic retry is blocked, but the customer may voluntarily retry in hosted Checkout."));
            case BANK_TEMP_ERROR -> record.getOutcome() == Outcome.ESCALATED
                    ? escalated()
                    : retryExhausted(record)
                    ? allowed(RecoveryCheckoutAction.TRY_PAYMENT_AGAIN, "Try Payment Again",
                            "Customer can make a voluntary payment attempt",
                            "Automatic bank-error retries are exhausted, so a separate customer-initiated payment is allowed.")
                    : blocked(NextRecoveryAction.AWAIT_SCHEDULED_RETRY, "Retry Scheduled",
                            "A bounded automatic retry remains scheduled",
                            "The existing retry policy still has an attempt available.",
                            "Wait for the scheduled retry before offering another Checkout.",
                            "Creating another order now could duplicate the payment attempt.");
            case MANDATE_FAILED_RETRYABLE -> record.getOutcome() == Outcome.ESCALATED
                    ? escalated()
                    : retryExhausted(record)
                    ? allowed(RecoveryCheckoutAction.PAY_MANUALLY, "Pay Manually",
                            "A voluntary one-time payment is available",
                            "Mandate retries are exhausted; this Checkout is a one-time customer payment, not another mandate debit.")
                    : blocked(NextRecoveryAction.AWAIT_SCHEDULED_MANDATE_RETRY, "Mandate Retry Scheduled",
                            "A bounded mandate retry remains scheduled",
                            "The mandate retry policy still has an attempt available.",
                            "Wait for the scheduled mandate retry.",
                            "Do not create a duplicate one-time payment while a mandate retry remains.");
            case MANDATE_EXPIRED -> blocked(NextRecoveryAction.ESCALATE_MANDATE_RENEWAL,
                    "Escalated for Review", "Mandate renewal and consent review are required",
                    "The current expired or revoked mandate case is escalated and cannot open Checkout.",
                    "Complete the required consent or renewal review first.",
                    "Do not use a one-time payment to bypass an escalated consent workflow.");
            case PAYMENT_PENDING -> blocked(NextRecoveryAction.VERIFY_PAYMENT_STATUS,
                    "Verify Payment Status First", "Original payment outcome is uncertain",
                    "The payment is pending and may still complete.",
                    "Confirm the existing payment status before attempting a new charge.",
                    "Creating another payment now could cause duplicate debit.");
            case WEAK_NETWORK -> blocked(NextRecoveryAction.VERIFY_PAYMENT_STATUS,
                    "Verify Previous Payment First", "Previous payment outcome must be confirmed",
                    "A network or client timeout does not prove that the original payment failed.",
                    "Verify the previous payment before offering a new Checkout.",
                    "A second payment could duplicate a successful but delayed debit.");
            case USER_CANCELLED -> blocked(NextRecoveryAction.STOPPED_BY_POLICY,
                    "No Automatic Recovery Allowed", "Customer cancellation is protected",
                    "The customer explicitly cancelled this transaction.",
                    "Respect the cancellation; do not create a recovery order from this record.",
                    "No automatic or operator-triggered collection is allowed.");
            case MERCHANT_GATEWAY_ISSUE -> blocked(NextRecoveryAction.ESCALATE_TO_MERCHANT,
                    "Merchant Review Required", "Merchant or gateway investigation is required",
                    "The failure is merchant or gateway-side, not a customer payment-method failure.",
                    "Resolve the merchant configuration or routing problem first.",
                    "Creating customer Checkout would not address the root cause.");
            case UNKNOWN -> blocked(NextRecoveryAction.ESCALATE_TO_MERCHANT,
                    "Manual Review Required", "The root cause is uncertain",
                    "Low-confidence or unknown cases require manual review.",
                    "Review the evidence before any financial action.",
                    "Automated Checkout is unsafe without a reliable classification.");
        };
    }

    private boolean retryExhausted(AuditRecord record) {
        int attempts = record.getAttemptNumber() == null ? 0 : record.getAttemptNumber();
        int maximum = record.getMaxAttemptsAllowed() == null ? 0 : record.getMaxAttemptsAllowed();
        return record.getLifecycleState() == LifecycleState.RETRY_EXHAUSTED
                || maximum <= 0 || attempts >= maximum;
    }

    private RecoveryCheckoutDecision eligibleUnlessEscalated(
            AuditRecord record, RecoveryCheckoutDecision eligible) {
        return record.getOutcome() == Outcome.ESCALATED ? escalated() : eligible;
    }

    private RecoveryCheckoutDecision escalated() {
        return blocked(NextRecoveryAction.ESCALATE_TO_MERCHANT, "Escalated for Review",
                "This case is under manual review", "Escalated cases cannot create recovery Checkout orders.",
                "Continue the existing merchant review workflow.",
                "A new payment could bypass required review controls.");
    }

    private RecoveryCheckoutDecision allowed(RecoveryCheckoutAction action, String button,
            String title, String reason) {
        return new RecoveryCheckoutDecision(true, NextRecoveryAction.CUSTOMER_RECOVERY_CHECKOUT,
                action, button, title, reason,
                "Open Razorpay Test Mode Checkout for a voluntary customer payment.",
                "The transaction is recovered only after backend signature verification.");
    }

    private RecoveryCheckoutDecision blocked(NextRecoveryAction recommendation, String button,
            String title, String reason, String nextStep, String riskNote) {
        return new RecoveryCheckoutDecision(false, recommendation, null, button, title, reason, nextStep, riskNote);
    }

    public record RecoveryCheckoutDecision(
            boolean allowed,
            NextRecoveryAction recommendedAction,
            RecoveryCheckoutAction recoveryAction,
            String buttonLabel,
            String title,
            String reason,
            String nextStep,
            String riskNote) {
    }
}
