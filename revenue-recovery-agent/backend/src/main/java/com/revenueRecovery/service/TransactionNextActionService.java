package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.NextRecoveryActionResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.NextActionMode;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.model.enums.NextRecoveryAction;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.PaymentReservationRepository;
import com.revenueRecovery.model.PaymentReservation;
import com.revenueRecovery.model.enums.ReservationStatus;
import com.revenueRecovery.model.enums.TransactionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionNextActionService {
    private final AuditRecordRepository auditRecordRepository;
    private final RecoveryDemoCaseRepository demoCaseRepository;
    private final RecoveryTransactionEligibilityService transactionEligibilityService;
    private final PaymentReservationRepository reservationRepository;

    @Autowired
    public TransactionNextActionService(AuditRecordRepository auditRecordRepository,
            RecoveryDemoCaseRepository demoCaseRepository,
            RecoveryTransactionEligibilityService transactionEligibilityService,
            PaymentReservationRepository reservationRepository) {
        this.auditRecordRepository = auditRecordRepository;
        this.demoCaseRepository = demoCaseRepository;
        this.transactionEligibilityService = transactionEligibilityService;
        this.reservationRepository = reservationRepository;
    }

    TransactionNextActionService(AuditRecordRepository auditRecordRepository,
            RecoveryDemoCaseRepository demoCaseRepository,
            RecoveryTransactionEligibilityService transactionEligibilityService) {
        this(auditRecordRepository, demoCaseRepository, transactionEligibilityService, null);
    }

    /** A read may expire a stale link to prevent an unrecoverable pending state. */
    @Transactional
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
        if (record.getRootCause() == com.revenueRecovery.model.enums.RootCause.WEAK_NETWORK
                && maximum > 0 && attempts < maximum && reservationRepository != null) {
            PaymentReservation reservation = reservationRepository.findFirstByEventIdOrderByIdDesc(record.getEventId()).orElse(null);
            if (reservation == null) return reservationDecision(record, attempts, maximum, "reservation_available",
                    NextRecoveryAction.RESERVE_PAYMENT, NextActionType.CREATE_RESERVATION,
                    "Reserve Payment", "Weak connection detected",
                    "Payment can be reserved for a duplicate-safe completion check when connectivity improves.");
            if (reservation.getStatus() == ReservationStatus.PENDING) return reservationDecision(record, attempts, maximum,
                    "reserved_pending", NextRecoveryAction.COMPLETE_RESERVATION, NextActionType.SIMULATE_RECONNECT,
                    "Simulate Reconnect", "Reserved (Pending)",
                    "Reconnect completes the safe demo reservation and records the recovered result.");
            if (reservation.getStatus() == ReservationStatus.EXPIRED_ALT_LINK_SENT) return reservationDecision(record,
                    attempts, maximum, "reserved_expired_alt_link_sent", NextRecoveryAction.SEND_ALT_PAYMENT_LINK,
                    NextActionType.NONE, "Alternative Link Sent", "Reserved (Expired, Alt Link Sent)",
                    "The reservation expired and moved to the capped alternative-payment fallback.");
        }
        RecoveryTransactionEligibilityService.RecoveryCheckoutDecision decision =
                transactionEligibilityService.evaluate(record);
        // A resumable customer checkout is the effective current state. Reporting the
        // original policy terminal state here produced the contradictory "Stopped" +
        // "Resume payment" card even though the link itself was valid and active.
        if (decision.actionType() == NextActionType.RESUME_RECOVERY_CHECKOUT) {
            lifecycle = "recovery_payment_pending";
        }
        return new NextRecoveryActionResponse(record.getEventId(), record.getOutcome(), record.getOutcome(), lifecycle,
                attempts, maximum, decision.allowed(), decision.recommendedAction(),
                decision.buttonLabel(), decision.title(), decision.reason(), decision.nextStep(),
                decision.riskNote(), decision.actionType(), decision.secondaryActionType(),
                decision.secondaryButtonLabel(), decision.existingLinkStatus(), decision.linkAgeMinutes(),
                NextActionMode.RAZORPAY_TEST_RECOVERY);
    }

    private NextRecoveryActionResponse reservationDecision(AuditRecord record, int attempts, int maximum,
            String lifecycle, NextRecoveryAction recommendation, NextActionType type, String button,
            String title, String reason) {
        return new NextRecoveryActionResponse(record.getEventId(), record.getOutcome(), record.getOutcome(), lifecycle,
                attempts, maximum, type != NextActionType.NONE, recommendation, button, title, reason,
                type == NextActionType.CREATE_RESERVATION ? "Create one 15-minute reservation."
                        : type == NextActionType.SIMULATE_RECONNECT ? "Re-verify payment status and complete only if verified."
                        : "Use the separate alternative-payment path.",
                "One pending reservation per order; completion is idempotent and verification-gated.", type,
                null, null, "none", 0, NextActionMode.RAZORPAY_TEST_RECOVERY);
    }

    private NextRecoveryActionResponse decideDemo(RecoveryDemoCase recoveryCase) {
        if (recoveryCase.getOutcome() == Outcome.RECOVERED
                || "recovered".equals(recoveryCase.getRecoveryStatus())) {
            return new NextRecoveryActionResponse(recoveryCase.getEventId(), Outcome.RECOVERED, Outcome.RECOVERED,
                    "recovered", 1, 1, false, NextRecoveryAction.ALREADY_RECOVERED,
                    "Already Recovered", "Recovered via verified Razorpay Test Mode payment",
                    "Verified Test Mode recovery was completed; the append-only audit history contains the result.",
                    "Review the verified recovery status and audit trail.",
                    "Do not initiate another payment for this recovered demo case.",
                    NextActionType.NONE, null, null, "recovered", 0, NextActionMode.RAZORPAY_TEST_DEMO);
        }

        return new NextRecoveryActionResponse(recoveryCase.getEventId(), Outcome.NOT_RECOVERED, Outcome.NOT_RECOVERED,
                recoveryCase.getRecoveryStatus(), 0, 1, true, NextRecoveryAction.SEND_RECOVERY_LINK,
                "Open Test Mode Recovery Checkout", "Recovery payment link is available",
                "This checkout-abandonment case is eligible for one customer-initiated Test Mode recovery payment link.",
                "Open Razorpay Test Mode Checkout. The case changes only after server-side payment verification.",
                "Test Mode only. No real money is charged, and an unverified callback cannot recover the case.",
                NextActionType.OPEN_TEST_MODE_RECOVERY_CHECKOUT, null, null, "none", 0,
                NextActionMode.RAZORPAY_TEST_DEMO);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
