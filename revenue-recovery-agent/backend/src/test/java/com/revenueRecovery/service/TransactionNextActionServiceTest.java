package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.NextRecoveryActionResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.NextActionMode;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.model.enums.NextRecoveryAction;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionNextActionServiceTest {
    @Mock AuditRecordRepository auditRecordRepository;
    @Mock RecoveryDemoCaseRepository demoCaseRepository;
    @Mock TransactionRecoveryPaymentLinkRepository transactionLinkRepository;

    private TransactionNextActionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionNextActionService(auditRecordRepository, demoCaseRepository,
                new RecoveryTransactionEligibilityService(transactionLinkRepository));
    }

    @Test
    void unknownEventIsNotFound() {
        when(auditRecordRepository.findByEventId("missing")).thenReturn(Optional.empty());
        when(demoCaseRepository.findByEventId("missing")).thenReturn(Optional.empty());
        assertThrows(TransactionNotFoundException.class, () -> service.decide("missing"));
    }

    @Test
    void recoveredAlwaysBlocksFurtherCollection() {
        NextRecoveryActionResponse result = decide(record("recovered", RootCause.CHECKOUT_ABANDONED,
                Outcome.RECOVERED, LifecycleState.RECOVERED, 1, 1));
        assertDecision(result, NextRecoveryAction.ALREADY_RECOVERED, NextActionType.NONE, false);
    }

    @Test
    void cancellationIsBlockedButAuthenticationFailureAllowsVoluntaryCheckout() {
        assertDecision(decide(record("cancelled", RootCause.USER_CANCELLED, Outcome.STOPPED_CORRECTLY,
                LifecycleState.STOPPED, 0, 0)), NextRecoveryAction.STOPPED_BY_POLICY, NextActionType.NONE, false);
        assertDecision(decide(record("pin", RootCause.INCORRECT_PIN, Outcome.STOPPED_CORRECTLY,
                LifecycleState.STOPPED, 0, 0)), NextRecoveryAction.TRY_PAYMENT_AGAIN_SECURELY,
                NextActionType.OPEN_RECOVERY_CHECKOUT, true);
    }

    @Test
    void uncertainGatewayAndExpiredMandateCasesOfferGuidanceOnly() {
        assertDecision(decide(record("unknown", RootCause.UNKNOWN, Outcome.ESCALATED,
                LifecycleState.ESCALATED, 0, 0)), NextRecoveryAction.ESCALATE_TO_MERCHANT,
                NextActionType.NONE, false);
        assertDecision(decide(record("gateway", RootCause.MERCHANT_GATEWAY_ISSUE, Outcome.ESCALATED,
                LifecycleState.ESCALATED, 0, 0)), NextRecoveryAction.ESCALATE_TO_MERCHANT,
                NextActionType.NONE, false);
        assertDecision(decide(record("mandate-expired", RootCause.MANDATE_EXPIRED, Outcome.ESCALATED,
                LifecycleState.ESCALATED, 0, 0)), NextRecoveryAction.ESCALATE_TO_MERCHANT,
                NextActionType.NONE, false);
    }

    @Test
    void pendingPaymentOffersStatusVerificationButNeverCheckout() {
        NextRecoveryActionResponse result = decide(record("pending", RootCause.PAYMENT_PENDING,
                Outcome.ESCALATED, LifecycleState.ESCALATED, 1, 1));
        assertDecision(result, NextRecoveryAction.ESCALATE_TO_MERCHANT,
                NextActionType.NONE, false);
    }

    @Test
    void boundedRetriesAreInformationalUntilExhausted() {
        NextRecoveryActionResponse weakScheduled = decide(record("weak-scheduled", RootCause.WEAK_NETWORK,
                Outcome.NOT_RECOVERED, LifecycleState.RETRY_SCHEDULED, 1, 2));
        assertDecision(weakScheduled, NextRecoveryAction.VERIFY_PAYMENT_STATUS, NextActionType.NONE, false);

        NextRecoveryActionResponse bankScheduled = decide(record("bank-scheduled", RootCause.BANK_TEMP_ERROR,
                Outcome.NOT_RECOVERED, LifecycleState.RETRY_SCHEDULED, 1, 2));
        assertDecision(bankScheduled, NextRecoveryAction.AWAIT_SCHEDULED_RETRY, NextActionType.NONE, false);

        NextRecoveryActionResponse mandateScheduled = decide(record("mandate-scheduled",
                RootCause.MANDATE_FAILED_RETRYABLE, Outcome.NOT_RECOVERED,
                LifecycleState.RETRY_SCHEDULED, 1, 2));
        assertDecision(mandateScheduled, NextRecoveryAction.AWAIT_SCHEDULED_MANDATE_RETRY,
                NextActionType.NONE, false);
    }

    @Test
    void exhaustedBankAndMandateRetriesAllowVoluntaryCheckoutButWeakNetworkDoesNot() {
        NextRecoveryActionResponse weak = decide(record("weak", RootCause.WEAK_NETWORK,
                Outcome.NOT_RECOVERED, LifecycleState.RETRY_EXHAUSTED, 2, 2));
        assertDecision(weak, NextRecoveryAction.VERIFY_PAYMENT_STATUS, NextActionType.NONE, false);
        for (RootCause cause : new RootCause[] { RootCause.BANK_TEMP_ERROR,
                RootCause.MANDATE_FAILED_RETRYABLE }) {
            NextRecoveryActionResponse result = decide(record("exhausted-" + cause.name(), cause,
                    Outcome.NOT_RECOVERED, LifecycleState.RETRY_EXHAUSTED, 2, 2));
            assertDecision(result, cause == RootCause.BANK_TEMP_ERROR
                            ? NextRecoveryAction.TRY_PAYMENT_AGAIN : NextRecoveryAction.PAY_MANUALLY,
                    NextActionType.OPEN_RECOVERY_CHECKOUT, true);
        }
    }

    @Test
    void safeBenchmarkCasesOfferCustomerInitiatedCheckout() {
        assertDecision(decide(record("checkout", RootCause.CHECKOUT_ABANDONED, Outcome.NOT_RECOVERED,
                LifecycleState.NOT_RECOVERED, 1, 1)), NextRecoveryAction.RESUME_PAYMENT,
                NextActionType.OPEN_RECOVERY_CHECKOUT, true);
        assertDecision(decide(record("balance", RootCause.INSUFFICIENT_BALANCE, Outcome.NOT_RECOVERED,
                LifecycleState.NOT_RECOVERED, 1, 1)), NextRecoveryAction.CHOOSE_ANOTHER_PAYMENT_METHOD,
                NextActionType.OPEN_RECOVERY_CHECKOUT, true);
    }

    @Test
    void dedicatedDemoAloneCanOpenTestCheckoutUntilVerified() {
        RecoveryDemoCase demo = demo(Outcome.NOT_RECOVERED, "awaiting_customer_payment");
        NextRecoveryActionResponse eligible = decideDemo(demo);
        assertDecision(eligible, NextRecoveryAction.SEND_RECOVERY_LINK,
                NextActionType.OPEN_TEST_MODE_RECOVERY_CHECKOUT, true);
        assertEquals(NextActionMode.RAZORPAY_TEST_DEMO, eligible.mode());

        demo.setOutcome(Outcome.RECOVERED);
        demo.setRecoveryStatus("recovered");
        NextRecoveryActionResponse recovered = decideDemo(demo);
        assertDecision(recovered, NextRecoveryAction.ALREADY_RECOVERED, NextActionType.NONE, false);
    }

    private NextRecoveryActionResponse decide(AuditRecord record) {
        when(auditRecordRepository.findByEventId(record.getEventId())).thenReturn(Optional.of(record));
        return service.decide(record.getEventId());
    }

    private NextRecoveryActionResponse decideDemo(RecoveryDemoCase demo) {
        when(auditRecordRepository.findByEventId(demo.getEventId())).thenReturn(Optional.empty());
        when(demoCaseRepository.findByEventId(demo.getEventId())).thenReturn(Optional.of(demo));
        return service.decide(demo.getEventId());
    }

    private AuditRecord record(String eventId, RootCause cause, Outcome outcome,
            LifecycleState lifecycle, int attempts, int maximum) {
        AuditRecord record = new AuditRecord();
        record.setEventId(eventId);
        record.setRootCause(cause);
        record.setOutcome(outcome);
        record.setLifecycleState(lifecycle);
        record.setAttemptNumber(attempts);
        record.setMaxAttemptsAllowed(maximum);
        return record;
    }

    private RecoveryDemoCase demo(Outcome outcome, String status) {
        RecoveryDemoCase demo = new RecoveryDemoCase();
        demo.setEventId(RecoveryDemoCaseSeeder.DEMO_EVENT_ID);
        demo.setRootCause(RootCause.CHECKOUT_ABANDONED);
        demo.setAmount(new BigDecimal("500.00"));
        demo.setCurrency("INR");
        demo.setOutcome(outcome);
        demo.setRecoveryStatus(status);
        demo.setDemoOnly(true);
        return demo;
    }

    private void assertDecision(NextRecoveryActionResponse result, NextRecoveryAction recommendation,
            NextActionType type, boolean allowed) {
        assertEquals(recommendation, result.recommendedAction());
        assertEquals(type, result.actionType());
        assertEquals(allowed, result.actionAllowed());
        if (type != NextActionType.OPEN_TEST_MODE_RECOVERY_CHECKOUT
                && type != NextActionType.OPEN_RECOVERY_CHECKOUT) {
            assertFalse(result.buttonLabel().toLowerCase().contains("recover amount"));
        }
    }
}
