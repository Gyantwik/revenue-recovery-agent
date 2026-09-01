package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.model.enums.NextRecoveryAction;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RecoveryCheckoutAction;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class RecoveryTransactionEligibilityService {
    public static final String NONE = "none";
    public static final String CHECKOUT_OPENED = "checkout_opened";
    public static final String ABANDONED = "abandoned";
    public static final long ACTIVE_MINUTES = 30;
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            RecoveryPaymentLinkService.ORDER_CREATED,
            CHECKOUT_OPENED,
            RazorpayCheckoutEventService.UNVERIFIED);

    private final TransactionRecoveryPaymentLinkRepository linkRepository;

    public RecoveryTransactionEligibilityService(TransactionRecoveryPaymentLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Transactional
    public RecoveryCheckoutDecision evaluate(AuditRecord record) {
        RecoveryCheckoutDecision policy = evaluatePolicy(record);
        if (!policy.allowed()) return policy;

        TransactionRecoveryPaymentLink link = linkRepository.findByEventEventId(record.getEventId()).orElse(null);
        if (link == null) return policy.withLink(NONE, 0);

        long ageMinutes = ageMinutes(link, Instant.now());
        if (RecoveryPaymentFinalizationService.LINK_RECOVERED.equals(link.getStatus())
                || RazorpaySignatureVerificationService.VERIFIED.equals(link.getStatus())) {
            return alreadyRecovered(link.getStatus(), ageMinutes);
        }

        if (ACTIVE_STATUSES.contains(link.getStatus()) && ageMinutes < ACTIVE_MINUTES) {
            return new RecoveryCheckoutDecision(true, policy.recommendedAction(), policy.recoveryAction(),
                    NextActionType.RESUME_RECOVERY_CHECKOUT, NextActionType.CHECK_PAYMENT_STATUS,
                    "Resume Recovery Payment", "Check Payment Status",
                    "A linked recovery payment can be resumed",
                    "A non-stale Test Mode order already exists for this transaction.",
                    "Resume the same hosted Checkout or check whether its reported payment can be verified.",
                    "The same order is reused to prevent duplicate collection.", link.getStatus(), ageMinutes);
        }

        if (ACTIVE_STATUSES.contains(link.getStatus())
                || RazorpaySignatureVerificationService.FAILED.equals(link.getStatus())) {
            link.setStatus(ABANDONED);
            linkRepository.save(link);
        }

        if (ABANDONED.equals(link.getStatus())) {
            return new RecoveryCheckoutDecision(true, policy.recommendedAction(), policy.recoveryAction(),
                    NextActionType.OPEN_RECOVERY_CHECKOUT, null,
                    "Retry Payment", null, "The previous recovery order expired",
                    "The previous unverified recovery link is stale or failed and has been abandoned.",
                    "Create a fresh Test Mode order for this persisted transaction.",
                    "Only the fresh order can be used; verified recovery is still required.", ABANDONED, ageMinutes);
        }

        return blocked(policy.recommendedAction(), "Manual Review Required",
                "Recovery link status requires review", "The existing recovery link is not resumable.",
                "Review the stored link before creating another order.",
                "A second order is blocked until the existing mapping is resolved.", link.getStatus(), ageMinutes);
    }

    public RecoveryCheckoutDecision requireEligibleForNewOrder(AuditRecord record) {
        RecoveryCheckoutDecision decision = evaluate(record);
        if (!decision.allowed()
                || (decision.actionType() != NextActionType.OPEN_RECOVERY_CHECKOUT
                && decision.actionType() != NextActionType.RESUME_RECOVERY_CHECKOUT)) {
            throw RecoveryPaymentException.notAllowed(decision.reason());
        }
        return decision;
    }

    public RecoveryCheckoutDecision requireEligibleForFinalization(AuditRecord record) {
        RecoveryCheckoutDecision decision = evaluatePolicy(record);
        if (!decision.allowed()) throw RecoveryPaymentException.notAllowed(decision.reason());
        return decision;
    }

    RecoveryCheckoutDecision evaluatePolicy(AuditRecord record) {
        if (record == null) {
            return blocked(NextRecoveryAction.STOPPED_BY_POLICY, "Transaction Not Found",
                    "Transaction is unavailable", "The persisted transaction could not be loaded.",
                    "Refresh and try again.", "No payment order was created.", NONE, 0);
        }
        if (record.getOutcome() == Outcome.RECOVERED) return alreadyRecovered(NONE, 0);
        if (record.getOutcome() == Outcome.ESCALATED) return escalated();

        return switch (record.getRootCause()) {
            case CHECKOUT_ABANDONED -> allowed(RecoveryCheckoutAction.RESUME_PAYMENT,
                    "Resume Payment", "Customer payment can be resumed",
                    "Checkout was abandoned before payment completion.");
            case INSUFFICIENT_BALANCE -> allowed(RecoveryCheckoutAction.CHOOSE_ANOTHER_PAYMENT_METHOD,
                    "Choose Another Payment Method", "A voluntary alternative payment can be attempted",
                    "The customer may add funds or choose another payment method.");
            case INCORRECT_PIN -> allowed(RecoveryCheckoutAction.TRY_PAYMENT_AGAIN_SECURELY,
                    "Try Payment Again Securely", "Customer-initiated secure retry is available",
                    "Automatic retry is blocked, but the customer may voluntarily retry in hosted Checkout.");
            case BANK_TEMP_ERROR -> retryExhausted(record)
                    ? allowed(RecoveryCheckoutAction.TRY_PAYMENT_AGAIN, "Try Payment Again",
                            "Customer can make a voluntary payment attempt",
                            "Automatic bank-error retries are exhausted.")
                    : blocked(NextRecoveryAction.AWAIT_SCHEDULED_RETRY, "Retry Scheduled",
                            "A bounded automatic retry remains scheduled",
                            "The existing retry policy still has an attempt available.",
                            "Wait for the scheduled retry before offering another Checkout.",
                            "Creating another order now could duplicate the payment attempt.", NONE, 0);
            case MANDATE_FAILED_RETRYABLE -> retryExhausted(record)
                    ? allowed(RecoveryCheckoutAction.PAY_MANUALLY, "Pay Manually",
                            "A voluntary one-time payment is available",
                            "Mandate retries are exhausted; this is not another mandate debit.")
                    : blocked(NextRecoveryAction.AWAIT_SCHEDULED_MANDATE_RETRY, "Mandate Retry Scheduled",
                            "A bounded mandate retry remains scheduled",
                            "The mandate retry policy still has an attempt available.",
                            "Wait for the scheduled mandate retry.",
                            "Do not create a duplicate payment while a mandate retry remains.", NONE, 0);
            case MANDATE_EXPIRED -> allowed(RecoveryCheckoutAction.PAY_MANUALLY, "Pay Manually",
                    "A voluntary one-time payment is available",
                    "The expired mandate cannot be debited; a new customer-authorized one-time payment is allowed.");
            case PAYMENT_PENDING -> blocked(NextRecoveryAction.VERIFY_PAYMENT_STATUS,
                    "Verify Payment Status First", "Original payment outcome is uncertain",
                    "The payment is pending and may still complete.",
                    "Confirm the existing payment status before attempting a new charge.",
                    "Creating another payment now could cause duplicate debit.", NONE, 0);
            case WEAK_NETWORK -> blocked(NextRecoveryAction.VERIFY_PAYMENT_STATUS,
                    "Verify Previous Payment First", "Previous payment outcome must be confirmed",
                    "A network or client timeout does not prove that the original payment failed.",
                    "Verify the previous payment before offering a new Checkout.",
                    "A second payment could duplicate a delayed debit.", NONE, 0);
            case USER_CANCELLED -> blocked(NextRecoveryAction.STOPPED_BY_POLICY,
                    "No Automatic Recovery Allowed", "Customer cancellation is protected",
                    "User Cancelled cases cannot be recovered through a new payment.",
                    "Respect the cancellation and do not create a recovery order.",
                    "No operator-triggered collection is allowed.", NONE, 0);
            case MERCHANT_GATEWAY_ISSUE -> blocked(NextRecoveryAction.ESCALATE_TO_MERCHANT,
                    "Merchant Review Required", "Merchant or gateway investigation is required",
                    "The failure is merchant or gateway-side.",
                    "Resolve the merchant configuration or routing problem first.",
                    "Creating customer Checkout would not address the root cause.", NONE, 0);
            case UNKNOWN -> blocked(NextRecoveryAction.ESCALATE_TO_MERCHANT,
                    "Manual Review Required", "The root cause is uncertain",
                    "Low-confidence or unknown cases require manual review.",
                    "Review the evidence before any financial action.",
                    "Automated Checkout is unsafe without a reliable classification.", NONE, 0);
        };
    }

    public boolean isActive(TransactionRecoveryPaymentLink link, Instant now) {
        return link != null && ACTIVE_STATUSES.contains(link.getStatus())
                && ageMinutes(link, now) < ACTIVE_MINUTES;
    }

    public long ageMinutes(TransactionRecoveryPaymentLink link, Instant now) {
        if (link == null || link.getCreatedAt() == null) return 0;
        return Math.max(0, Duration.between(link.getCreatedAt(), now).toMinutes());
    }

    private boolean retryExhausted(AuditRecord record) {
        int attempts = record.getAttemptNumber() == null ? 0 : record.getAttemptNumber();
        int maximum = record.getMaxAttemptsAllowed() == null ? 0 : record.getMaxAttemptsAllowed();
        return maximum > 0 && attempts >= maximum;
    }

    private RecoveryCheckoutDecision escalated() {
        return blocked(NextRecoveryAction.ESCALATE_TO_MERCHANT, "Escalated for Review",
                "This case is under manual review", "Escalated cases cannot create recovery Checkout orders.",
                "Continue the existing merchant review workflow.",
                "A new payment could bypass required review controls.", NONE, 0);
    }

    private RecoveryCheckoutDecision alreadyRecovered(String status, long ageMinutes) {
        return blocked(NextRecoveryAction.ALREADY_RECOVERED, "Already Recovered",
                "Payment recovery is complete", "This transaction is already recovered.",
                "Review the existing outcome and audit history.",
                "Do not initiate another payment.", status, ageMinutes);
    }

    private RecoveryCheckoutDecision allowed(RecoveryCheckoutAction action, String button,
            String title, String reason) {
        return new RecoveryCheckoutDecision(true, NextRecoveryAction.valueOf(action.name()), action,
                NextActionType.OPEN_RECOVERY_CHECKOUT, null, button, null, title, reason,
                "Open Razorpay Test Mode Checkout for a voluntary customer payment.",
                "The transaction is recovered only after backend signature verification.", NONE, 0);
    }

    private RecoveryCheckoutDecision blocked(NextRecoveryAction recommendation, String button,
            String title, String reason, String nextStep, String riskNote,
            String linkStatus, long linkAgeMinutes) {
        return new RecoveryCheckoutDecision(false, recommendation, null, NextActionType.NONE, null,
                button, null, title, reason, nextStep, riskNote, linkStatus, linkAgeMinutes);
    }

    public record RecoveryCheckoutDecision(
            boolean allowed,
            NextRecoveryAction recommendedAction,
            RecoveryCheckoutAction recoveryAction,
            NextActionType actionType,
            NextActionType secondaryActionType,
            String buttonLabel,
            String secondaryButtonLabel,
            String title,
            String reason,
            String nextStep,
            String riskNote,
            String existingLinkStatus,
            long linkAgeMinutes) {
        RecoveryCheckoutDecision withLink(String status, long ageMinutes) {
            return new RecoveryCheckoutDecision(allowed, recommendedAction, recoveryAction, actionType,
                    secondaryActionType, buttonLabel, secondaryButtonLabel, title, reason, nextStep,
                    riskNote, status, ageMinutes);
        }
    }
}
