package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.enums.*;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lifecycle-testdb")
@Transactional
class RecoveryLifecycleTest {
    @Autowired RevenueRecoveryPipeline pipeline;
    @Autowired RecoveryActionService actionService;
    @Autowired AuditRecordRepository auditRepository;
    @Autowired AuditHistoryRepository historyRepository;

    @Test
    void userCancelledAndIncorrectPinStopWithZeroAttempts() {
        AuditRecord cancelled = process("cancelled", "CANCELLED_BY_USER");
        AuditRecord pin = process("pin", "INVALID_PIN");
        for (AuditRecord record : List.of(cancelled, pin)) {
            assertEquals(LifecycleState.STOPPED, record.getLifecycleState());
            assertEquals(Outcome.STOPPED_CORRECTLY, record.getOutcome());
            assertEquals(0, record.getAttemptNumber());
            assertEquals(0, record.getMaxAttemptsAllowed());
            assertThrows(RecoveryActionBlockedException.class,
                    () -> actionService.triggerRetry(record.getEventId(), "blocked-retry"));
        }
    }

    @Test
    void pendingPaymentAllowsVerificationButRejectsRetry() {
        AuditRecord pending = active(process("pending", "PENDING_WITH_BANK"));
        assertThrows(RecoveryActionBlockedException.class,
                () -> actionService.triggerRetry(pending.getEventId(), "pending-retry"));
        assertDoesNotThrow(() -> actionService.trigger(
                pending.getEventId(), ActionTaken.VERIFY_STATUS, "pending-verify"));
        assertTrue(auditRepository.findByEventId(pending.getEventId()).orElseThrow().getLifecycleState().isTerminal());
    }

    @Test
    void bankTemporaryErrorAllowsTwoRetriesAndRejectsThird() {
        String id = failingTwiceId("bank");
        AuditRecord bank = active(processExact(id, "BANK_TIMEOUT"));
        actionService.triggerRetry(id, "bank-one");
        assertEquals(LifecycleState.RETRY_SCHEDULED, auditRepository.findByEventId(id).orElseThrow().getLifecycleState());
        actionService.triggerRetry(id, "bank-two");
        AuditRecord exhausted = auditRepository.findByEventId(id).orElseThrow();
        assertEquals(2, exhausted.getAttemptNumber());
        assertEquals(LifecycleState.RETRY_EXHAUSTED, exhausted.getLifecycleState());
        assertThrows(RecoveryActionBlockedException.class, () -> actionService.triggerRetry(id, "bank-three"));
    }

    @Test
    void expiredBankWindowBecomesRetryExhausted() {
        AuditRecord bank = active(process("expired", "BANK_TIMEOUT"));
        bank.setRecoveryWindowExpiresAt(Instant.now().minusSeconds(1));
        auditRepository.save(bank);
        assertThrows(RecoveryActionBlockedException.class,
                () -> actionService.triggerRetry(bank.getEventId(), "expired-window"));
        assertEquals(LifecycleState.RETRY_EXHAUSTED,
                auditRepository.findByEventId(bank.getEventId()).orElseThrow().getLifecycleState());
    }

    @Test
    void weakNetworkAndMandateRetriesAreBoundedAtTwo() {
        for (String[] scenario : List.of(
                new String[]{failingTwiceId("weak"), "NETWORK_DROP"},
                new String[]{failingTwiceId("mandate"), "DECLINED_BY_BANK"})) {
            AuditRecord record = active(processExact(scenario[0], scenario[1]));
            actionService.triggerRetry(record.getEventId(), "one");
            actionService.triggerRetry(record.getEventId(), "two");
            AuditRecord result = auditRepository.findByEventId(record.getEventId()).orElseThrow();
            assertEquals(2, result.getAttemptNumber());
            assertTrue(result.getAttemptNumber() <= result.getMaxAttemptsAllowed());
            assertThrows(RecoveryActionBlockedException.class,
                    () -> actionService.triggerRetry(record.getEventId(), "three"));
        }
    }

    @Test
    void customerLinksAreSingleUse() {
        AuditRecord balance = active(process("balance", "INSUFFICIENT_FUNDS"));
        actionService.trigger(balance.getEventId(), ActionTaken.SEND_ALT_PAYMENT_LINK, "balance-link");
        assertThrows(RecoveryActionBlockedException.class, () -> actionService.trigger(
                balance.getEventId(), ActionTaken.SEND_ALT_PAYMENT_LINK, "balance-link-two"));

        AuditRecord checkout = active(process("checkout", "TAB_CLOSED"));
        actionService.trigger(checkout.getEventId(), ActionTaken.SEND_RECOVERY_LINK, "checkout-link");
        assertThrows(RecoveryActionBlockedException.class, () -> actionService.trigger(
                checkout.getEventId(), ActionTaken.SEND_RECOVERY_LINK, "checkout-link-two"));
    }

    @Test
    void escalationPoliciesCannotRetry() {
        for (String[] scenario : List.of(
                new String[]{"gateway", "MID_INVALID"},
                new String[]{"expired-mandate", "MANDATE_NOT_FOUND"},
                new String[]{"unknown", "totally_unknown"})) {
            AuditRecord record = process(scenario[0], scenario[1]);
            assertEquals(LifecycleState.ESCALATED, record.getLifecycleState());
            assertEquals(0, record.getAttemptNumber());
            assertEquals(0, record.getMaxAttemptsAllowed());
            assertThrows(RecoveryActionBlockedException.class,
                    () -> actionService.triggerRetry(record.getEventId(), "forbidden"));
        }
    }

    @Test
    void duplicateKeyIsRejectedAndHistoryRemainsAppendOnly() {
        String id = failingTwiceId("duplicate");
        AuditRecord record = active(processExact(id, "BANK_TIMEOUT"));
        long before = historyRepository.countByEventId(id);
        actionService.triggerRetry(id, "same-key");
        long after = historyRepository.countByEventId(id);
        assertTrue(after > before);
        assertThrows(RecoveryActionBlockedException.class,
                () -> actionService.triggerRetry(id, "same-key"));
        assertEquals(after, historyRepository.countByEventId(id));
    }

    @Test
    void batchRerunDoesNotDuplicateHistory() throws Exception {
        AuditRecord record = process("idempotent-history", "BANK_TIMEOUT");
        long before = historyRepository.countByEventId(record.getEventId());
        pipeline.processEvent(event(record.getEventId(), "BANK_TIMEOUT"));
        assertEquals(before, historyRepository.countByEventId(record.getEventId()));
        assertEquals(1, auditRepository.findAll().stream()
                .filter(item -> item.getEventId().equals(record.getEventId())).count());
    }

    @Test
    void phaseTwoRecordIsBackfilledOnceOnRerun() {
        AuditRecord record = process("legacy-pending", "PENDING_WITH_BANK");
        historyRepository.deleteAll();
        record.setLifecycleState(null);
        record.setOutcome(Outcome.NOT_RECOVERED);
        auditRepository.save(record);

        AuditRecord migrated = pipeline.processEvent(event(record.getEventId(), "PENDING_WITH_BANK"));
        assertEquals(LifecycleState.ESCALATED, migrated.getLifecycleState());
        assertEquals(Outcome.ESCALATED, migrated.getOutcome());
        assertEquals(6, historyRepository.countByEventId(record.getEventId()));

        pipeline.processEvent(event(record.getEventId(), "PENDING_WITH_BANK"));
        assertEquals(6, historyRepository.countByEventId(record.getEventId()));
    }

    private AuditRecord process(String prefix, String signal) {
        return processExact(prefix + "-phase3", signal);
    }

    private AuditRecord processExact(String eventId, String signal) {
        return pipeline.processEvent(event(eventId, signal));
    }

    private Event event(String id, String signal) {
        Event event = new Event();
        event.setEventId(id);
        event.setCaseType(signal.contains("MANDATE") || signal.equals("DECLINED_BY_BANK") ? "mandate_renewal" : "payment_degradation");
        event.setAmount(new BigDecimal("125.00"));
        event.setCurrency("INR");
        event.setTimestamp(Instant.now());
        event.setSignalsUsed(List.of(signal));
        return event;
    }

    private AuditRecord active(AuditRecord record) {
        record.setLifecycleState(LifecycleState.ACTION_APPROVED);
        record.setAttemptNumber(0);
        record.setOutcome(null);
        record.setRecoveredAmount(BigDecimal.ZERO.setScale(2));
        record.setNextEligibleActionAt(Instant.now().minusSeconds(1));
        if (record.getActionTaken() == ActionTaken.RETRY_PAYMENT) {
            record.setRecoveryWindowExpiresAt(Instant.now().plusSeconds(1800));
        }
        return auditRepository.save(record);
    }

    private String failingTwiceId(String prefix) {
        for (int index = 0; index < 10000; index++) {
            String id = prefix + "-" + index + "-phase3";
            if (Math.floorMod((id + ":1").hashCode(), 100) >= 60
                    && Math.floorMod((id + ":2").hashCode(), 100) >= 60) return id;
        }
        throw new IllegalStateException("No deterministic failing event ID found");
    }
}
