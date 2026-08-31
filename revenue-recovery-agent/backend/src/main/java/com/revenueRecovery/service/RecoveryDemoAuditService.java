package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditHistory;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class RecoveryDemoAuditService {
    public static final String DECISION_REASON =
            "Eligible checkout-abandonment recovery completed through verified Razorpay Test Mode payment.";
    public static final String ACTION_RESULT = "RECOVERY_PAYMENT_VERIFIED_TEST_MODE";

    private final AuditHistoryRepository repository;

    public RecoveryDemoAuditService(AuditHistoryRepository repository) {
        this.repository = repository;
    }

    public AuditHistory appendVerifiedRecovery(RecoveryDemoCase recoveryCase,
            String razorpayOrderId, String razorpayPaymentId, Instant timestamp) {
        AuditHistory history = new AuditHistory();
        history.setEventId(recoveryCase.getEventId());
        history.setTimestamp(timestamp);
        history.setPreviousState(LifecycleState.RECOVERY_LINK_SENT);
        history.setNewState(LifecycleState.RECOVERED);
        history.setRootCause(recoveryCase.getRootCause());
        history.setClassificationConfidence(new BigDecimal("1.000"));
        history.setPolicyRuleMatched("Checkout abandoned → Send recovery link");
        history.setActionTaken(recoveryCase.getPolicyAction());
        history.setAttemptNumber(1);
        history.setMaxAttemptsAllowed(1);
        history.setOutcomeIfTerminal(Outcome.RECOVERED);
        history.setReason(DECISION_REASON + " result=" + ACTION_RESULT
                + "; mode=TEST; order_id=" + razorpayOrderId + "; payment_id=" + razorpayPaymentId);
        history.setActor(AuditActor.RAZORPAY_TEST_VERIFICATION);
        history.setIdempotencyKey(recoveryCase.getEventId() + ":razorpay-test-recovery:"
                + razorpayOrderId + ":" + razorpayPaymentId);
        return repository.saveAndFlush(history);
    }
}
