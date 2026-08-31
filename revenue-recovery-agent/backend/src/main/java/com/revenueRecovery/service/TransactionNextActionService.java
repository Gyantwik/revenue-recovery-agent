package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.NextRecoveryActionResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.NextActionMode;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.model.enums.NextRecoveryAction;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionNextActionService {
    private final AuditRecordRepository auditRecordRepository;
    private final RecoveryDemoCaseRepository demoCaseRepository;

    public TransactionNextActionService(AuditRecordRepository auditRecordRepository,
            RecoveryDemoCaseRepository demoCaseRepository) {
        this.auditRecordRepository = auditRecordRepository;
        this.demoCaseRepository = demoCaseRepository;
    }

    /** Read-only by design: asking what is safe must never execute or acknowledge an action. */
    @Transactional(readOnly = true)
    public NextRecoveryActionResponse decide(String eventId) {
        AuditRecord benchmarkRecord = auditRecordRepository.findByEventId(eventId).orElse(null);
        if (benchmarkRecord != null) return decideBenchmark(benchmarkRecord);

        RecoveryDemoCase demoCase = demoCaseRepository.findByEventId(eventId).orElse(null);
        if (demoCase != null) return decideDemo(demoCase);

        throw new TransactionNotFoundException(eventId);
    }

    private NextRecoveryActionResponse decideBenchmark(AuditRecord record) {
        int attempts = value(record.getAttemptNumber());
        int maximum = value(record.getMaxAttemptsAllowed());
        String lifecycle = record.getLifecycleState() == null
                ? "not_recovered" : record.getLifecycleState().toJson();

        if (record.getOutcome() == Outcome.RECOVERED) {
            return response(record, lifecycle, attempts, maximum, false,
                    NextRecoveryAction.ALREADY_RECOVERED, "Already Recovered",
                    "Payment recovery is complete",
                    "This payment or recovery has already completed; no further collection action is allowed.",
                    "Review the existing outcome and append-only audit history if confirmation is needed.",
                    "Do not initiate another payment or recovery attempt.", NextActionType.NONE);
        }

        return switch (record.getRootCause()) {
            case USER_CANCELLED -> response(record, lifecycle, attempts, maximum, false,
                    NextRecoveryAction.STOPPED_BY_POLICY, "No Further Action Allowed",
                    "No further action allowed",
                    "The customer cancelled the payment; do not retry or send a payment link.",
                    "Respect the customer decision and close the automated recovery workflow.",
                    "No retry, recovery link, or forced collection action may be initiated.", NextActionType.NONE);
            case INCORRECT_PIN -> response(record, lifecycle, attempts, maximum, false,
                    NextRecoveryAction.STOPPED_BY_POLICY, "No Further Action Allowed",
                    "No further action allowed",
                    "Authentication failure must not be retried automatically.",
                    "The customer must initiate a fresh authenticated payment themselves.",
                    "Do not retry, send a recovery link, or bypass authentication controls.", NextActionType.NONE);
            case UNKNOWN -> response(record, lifecycle, attempts, maximum, true,
                    NextRecoveryAction.ESCALATE_TO_MERCHANT, "Escalate to Merchant",
                    "Manual review is required",
                    "The root cause is uncertain, so an automated financial action is unsafe.",
                    "Review the transaction evidence and escalate it for manual resolution.",
                    "Escalation is guidance only; it does not create a charge or mark the case recovered.",
                    NextActionType.DISPLAY_INFORMATION);
            case MERCHANT_GATEWAY_ISSUE -> response(record, lifecycle, attempts, maximum, true,
                    NextRecoveryAction.ESCALATE_TO_MERCHANT, "Escalate to Merchant",
                    "Merchant or gateway investigation is required",
                    "A merchant or gateway issue requires investigation; do not create a customer charge.",
                    "Escalate the routing or merchant configuration issue for manual resolution.",
                    "This is a non-payment workflow recommendation only.", NextActionType.DISPLAY_INFORMATION);
            case MANDATE_EXPIRED -> response(record, lifecycle, attempts, maximum, true,
                    NextRecoveryAction.ESCALATE_MANDATE_RENEWAL, "Review Mandate / Escalate",
                    "Mandate renewal is required",
                    "The mandate expired or was revoked, so customer consent must be renewed before another debit.",
                    "Review the mandate and escalate the case for a consent-safe renewal flow.",
                    "Do not retry a debit against an expired or revoked mandate.", NextActionType.DISPLAY_INFORMATION);
            case PAYMENT_PENDING -> response(record, lifecycle, attempts, maximum, true,
                    NextRecoveryAction.VERIFY_PAYMENT_STATUS, "Verify Payment Status",
                    "Verify the original payment first",
                    "The original payment is pending and must be verified before initiating any new charge.",
                    "Review the original payment status through an approved operational process.",
                    "Avoid a duplicate debit; this button performs no polling or payment action.",
                    NextActionType.DISPLAY_INFORMATION);
            case WEAK_NETWORK -> retryDecision(record, lifecycle, attempts, maximum, false);
            case BANK_TEMP_ERROR -> retryDecision(record, lifecycle, attempts, maximum, false);
            case MANDATE_FAILED_RETRYABLE -> retryDecision(record, lifecycle, attempts, maximum, true);
            case CHECKOUT_ABANDONED -> response(record, lifecycle, attempts, maximum, false,
                    NextRecoveryAction.SEND_RECOVERY_LINK, "Send Recovery Link",
                    "Recovery-link guidance is available",
                    "Checkout abandonment can support a customer-initiated recovery link under controlled policy.",
                    "Use the separate dedicated Razorpay Test Mode recovery demo to exercise this flow.",
                    "Benchmark rows are read-only and cannot create payment orders in this phase.", NextActionType.NONE);
            case INSUFFICIENT_BALANCE -> response(record, lifecycle, attempts, maximum, false,
                    NextRecoveryAction.SEND_ALT_PAYMENT_LINK, "Alternative Payment Link Available",
                    "Alternative-payment guidance is available",
                    "This case may be eligible for an alternative payment link in a future controlled recovery flow.",
                    "Contact the customer through an approved future workflow; this benchmark row remains read-only.",
                    "No real link, charge, or recovered outcome is created in this phase.", NextActionType.NONE);
        };
    }

    private NextRecoveryActionResponse retryDecision(AuditRecord record, String lifecycle,
            int attempts, int maximum, boolean mandate) {
        boolean exhausted = record.getLifecycleState() == LifecycleState.RETRY_EXHAUSTED
                || maximum <= 0 || attempts >= maximum;
        if (exhausted) {
            return response(record, lifecycle, attempts, maximum, true,
                    NextRecoveryAction.ESCALATE_AFTER_RETRY_EXHAUSTED,
                    mandate ? "Review Mandate / Escalate" : "Review / Escalate",
                    "Automatic retries are exhausted",
                    mandate
                            ? "This mandate retry has reached its maximum of " + maximum
                                    + " attempts. Further automatic retries are blocked."
                            : "This recovery has reached its maximum of " + maximum
                                    + " attempts. Further automatic retries are blocked.",
                    mandate
                            ? "Review the mandate or escalate the case for manual resolution."
                            : "Verify the original status and escalate the case for manual resolution.",
                    mandate
                            ? "Do not initiate another automatic debit attempt."
                            : "Do not create another payment order or automatic retry.",
                    NextActionType.DISPLAY_INFORMATION);
        }

        return response(record, lifecycle, attempts, maximum, false,
                mandate ? NextRecoveryAction.AWAIT_SCHEDULED_MANDATE_RETRY
                        : NextRecoveryAction.AWAIT_SCHEDULED_RETRY,
                mandate ? "Mandate Retry Scheduled" : "Retry Scheduled",
                mandate ? "A bounded mandate retry is scheduled" : "A bounded retry is scheduled",
                mandate
                        ? "The existing bounded mandate-retry workflow still has attempts remaining."
                        : "The existing policy retry still has attempts remaining.",
                "Allow the scheduled synthetic workflow to proceed under its existing attempt limit.",
                "Do not force a retry or create a second payment order.", NextActionType.NONE);
    }

    private NextRecoveryActionResponse decideDemo(RecoveryDemoCase recoveryCase) {
        if (recoveryCase.getOutcome() == Outcome.RECOVERED
                || "recovered".equals(recoveryCase.getRecoveryStatus())) {
            return new NextRecoveryActionResponse(recoveryCase.getEventId(), Outcome.RECOVERED,
                    "recovered", 1, 1, false, NextRecoveryAction.ALREADY_RECOVERED,
                    "Already Recovered", "Recovered via verified Razorpay Test Mode payment",
                    "Verified Test Mode recovery was completed; the append-only audit history contains the result.",
                    "Review the verified recovery status and audit trail.",
                    "Do not initiate another payment for this recovered demo case.",
                    NextActionType.NONE, NextActionMode.RAZORPAY_TEST_DEMO);
        }

        return new NextRecoveryActionResponse(recoveryCase.getEventId(), Outcome.NOT_RECOVERED,
                recoveryCase.getRecoveryStatus(), 0, 1, true, NextRecoveryAction.SEND_RECOVERY_LINK,
                "Open Test Mode Recovery Checkout", "Recovery payment link is available",
                "This checkout-abandonment case is eligible for one customer-initiated Test Mode recovery payment link.",
                "Open Razorpay Test Mode Checkout. The case changes only after server-side payment verification.",
                "Test Mode only. No real money is charged, and an unverified callback cannot recover the case.",
                NextActionType.OPEN_TEST_MODE_RECOVERY_CHECKOUT, NextActionMode.RAZORPAY_TEST_DEMO);
    }

    private NextRecoveryActionResponse response(AuditRecord record, String lifecycle, int attempts, int maximum,
            boolean allowed, NextRecoveryAction recommendation, String button, String title, String reason,
            String nextStep, String riskNote, NextActionType actionType) {
        return new NextRecoveryActionResponse(record.getEventId(), record.getOutcome(), lifecycle,
                attempts, maximum, allowed, recommendation, button, title, reason, nextStep, riskNote,
                actionType, NextActionMode.SYNTHETIC_BENCHMARK);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
