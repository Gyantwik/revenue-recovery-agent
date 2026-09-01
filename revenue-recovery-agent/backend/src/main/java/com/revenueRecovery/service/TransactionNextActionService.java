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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionNextActionService {
    private final AuditRecordRepository auditRecordRepository;
    private final RecoveryDemoCaseRepository demoCaseRepository;
    private final RecoveryTransactionEligibilityService transactionEligibilityService;

    public TransactionNextActionService(AuditRecordRepository auditRecordRepository,
            RecoveryDemoCaseRepository demoCaseRepository,
            RecoveryTransactionEligibilityService transactionEligibilityService) {
        this.auditRecordRepository = auditRecordRepository;
        this.demoCaseRepository = demoCaseRepository;
        this.transactionEligibilityService = transactionEligibilityService;
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
        RecoveryTransactionEligibilityService.RecoveryCheckoutDecision decision =
                transactionEligibilityService.evaluate(record);
        return new NextRecoveryActionResponse(record.getEventId(), record.getOutcome(), lifecycle,
                attempts, maximum, decision.allowed(), decision.recommendedAction(),
                decision.buttonLabel(), decision.title(), decision.reason(), decision.nextStep(),
                decision.riskNote(), decision.allowed() ? NextActionType.OPEN_RECOVERY_CHECKOUT : NextActionType.NONE,
                decision.allowed() ? NextActionMode.RAZORPAY_TEST_RECOVERY : NextActionMode.SYNTHETIC_BENCHMARK);
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

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
