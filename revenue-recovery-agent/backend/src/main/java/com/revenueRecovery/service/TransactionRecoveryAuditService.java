package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditHistory;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TransactionRecoveryAuditService {
    private final AuditHistoryRepository repository;

    public TransactionRecoveryAuditService(AuditHistoryRepository repository) {
        this.repository = repository;
    }

    public AuditHistory appendVerifiedRecovery(AuditRecord record,
            TransactionRecoveryPaymentLink link, LifecycleState previousState,
            String paymentId, Instant timestamp) {
        AuditHistory history = new AuditHistory();
        history.setEventId(record.getEventId());
        history.setTimestamp(timestamp);
        history.setPreviousState(previousState);
        history.setNewState(LifecycleState.RECOVERED_BY_VERIFIED_TEST_PAYMENT);
        history.setRootCause(record.getRootCause());
        history.setClassificationConfidence(record.getClassificationConfidence());
        history.setPolicyRuleMatched(record.getPolicyRuleMatched());
        history.setActionTaken(record.getActionTaken());
        history.setAttemptNumber(record.getAttemptNumber());
        history.setMaxAttemptsAllowed(record.getMaxAttemptsAllowed());
        history.setOutcomeIfTerminal(Outcome.RECOVERED);
        history.setReason("CUSTOMER_INITIATED_RECOVERY_VERIFIED_TEST_MODE; "
                + "Customer-initiated Test Mode recovery Checkout was verified by the backend; "
                + "action=" + link.getRecoveryAction() + "; order_id=" + link.getRazorpayOrderId()
                + "; payment_id=" + paymentId);
        history.setActor(AuditActor.RAZORPAY_TEST_VERIFICATION);
        history.setIdempotencyKey(idempotencyKey(record.getEventId(), link.getRazorpayOrderId(), paymentId));
        return repository.saveAndFlush(history);
    }

    public String idempotencyKey(String eventId, String orderId, String paymentId) {
        return eventId + ":transaction-recovery:" + orderId + ":" + paymentId;
    }
}
