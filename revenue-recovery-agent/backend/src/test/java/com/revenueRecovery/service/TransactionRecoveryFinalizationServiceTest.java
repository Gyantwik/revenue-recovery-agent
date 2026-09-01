package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.BatchSummaryResponse;
import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationRequest;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RecoveryCheckoutAction;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transaction-recovery-finalization;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "razorpay.key-id=rzp_test_not_a_real_credential",
        "razorpay.key-secret=deterministic_noncredential_fixture_secret"
})
@Transactional
class TransactionRecoveryFinalizationServiceTest {
    private static final String EVENT_ID = "TXN10044";
    private static final String REQUEST_ID = "req_txn_recovery_test";
    private static final String ORDER_ID = "order_txn_recovery_test";

    @Autowired BatchService batchService;
    @Autowired BatchSummaryService summaryService;
    @Autowired RecoveryPaymentFinalizationService finalizationService;
    @Autowired AuditRecordRepository auditRepository;
    @Autowired EventRepository eventRepository;
    @Autowired AuditHistoryRepository historyRepository;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired RazorpayTestCheckoutAttemptRepository attemptRepository;
    @Autowired TransactionRecoveryPaymentLinkRepository linkRepository;
    @Autowired RazorpaySignatureVerificationService verificationService;
    @Autowired TransactionRecoveryStatusService statusService;

    @BeforeEach
    void seed() throws Exception {
        batchService.runBatch();
        AuditRecord record = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        record.setOutcome(Outcome.NOT_RECOVERED);
        record.setRecoveredAmount(BigDecimal.ZERO.setScale(2));
        record.setLifecycleState(com.revenueRecovery.model.enums.LifecycleState.NOT_RECOVERED);
        auditRepository.saveAndFlush(record);
    }

    @Test
    void verifiedLinkedPaymentAtomicallyRecoversRecordAndUpdatesSummaryOnce() {
        AuditRecord beforeRecord = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        assertEquals(Outcome.NOT_RECOVERED, beforeRecord.getOutcome());
        BigDecimal amount = beforeRecord.getAmount();
        BatchSummaryResponse summaryBefore = summaryService.summarize();
        BigDecimal recoveredBefore = summaryBefore.totalRecovered();
        var causeBefore = summaryBefore.byCause().stream()
                .filter(row -> row.rootCause() == beforeRecord.getRootCause()).findFirst().orElseThrow();
        long historyBefore = historyRepository.countByEventId(EVENT_ID);
        RazorpayTestOrder order = createLinkedOrder(beforeRecord);

        var result = finalizationService.finalizeIfLinked(order, "pay_txn_recovery_test",
                Instant.parse("2026-09-01T10:00:00Z")).orElseThrow();

        AuditRecord recovered = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        assertEquals(EVENT_ID, result.eventId());
        assertEquals(Outcome.RECOVERED, recovered.getOutcome());
        assertEquals(LifecycleState.RECOVERED_BY_VERIFIED_TEST_PAYMENT, recovered.getLifecycleState());
        assertEquals(0, recovered.getRecoveredAmount().compareTo(amount));
        BatchSummaryResponse summaryAfter = summaryService.summarize();
        assertEquals(0, summaryAfter.totalRecovered().compareTo(recoveredBefore.add(amount)));
        var causeAfter = summaryAfter.byCause().stream()
                .filter(row -> row.rootCause() == beforeRecord.getRootCause()).findFirst().orElseThrow();
        assertEquals(0, causeAfter.recoveredAmount().compareTo(causeBefore.recoveredAmount().add(amount)));
        assertEquals(historyBefore + 1, historyRepository.countByEventId(EVENT_ID));
        assertEquals(RecoveryPaymentFinalizationService.LINK_RECOVERED,
                linkRepository.findByEventEventId(EVENT_ID).orElseThrow().getStatus());

        assertThrows(RecoveryPaymentException.class, () -> finalizationService.finalizeIfLinked(
                order, "pay_txn_recovery_test", Instant.parse("2026-09-01T10:01:00Z")));
        assertEquals(historyBefore + 1, historyRepository.countByEventId(EVENT_ID));
        assertThrows(RecoveryPaymentException.class, () -> finalizationService.finalizeIfLinked(
                order, "pay_different_test", Instant.parse("2026-09-01T10:02:00Z")));
        assertEquals(historyBefore + 1, historyRepository.countByEventId(EVENT_ID));
    }

    @Test
    void orderCreationStateAloneDoesNotRecoverTransaction() {
        AuditRecord record = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        createLinkedOrder(record);
        AuditRecord unchanged = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        assertEquals(Outcome.NOT_RECOVERED, unchanged.getOutcome());
        assertEquals(0, unchanged.getRecoveredAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void validGeneratedHmacRecoversOnlyTheExplicitlyLinkedEvent() {
        AuditRecord record = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        RazorpayTestOrder order = createLinkedOrder(record);
        String paymentId = "pay_generated_hmac_fixture";
        String signature = verificationService.calculateSignature(ORDER_ID, paymentId,
                "deterministic_noncredential_fixture_secret");

        RazorpayTestCheckoutAttempt attempt = new RazorpayTestCheckoutAttempt();
        attempt.setInternalRequestId(REQUEST_ID);
        attempt.setRazorpayOrderId(ORDER_ID);
        attempt.setRazorpayPaymentId(paymentId);
        attempt.setRazorpaySignature(signature);
        attempt.setEventType(RazorpayCheckoutEventService.SUCCESS);
        attempt.setStatus(RazorpayCheckoutEventService.UNVERIFIED);
        attempt.setCreatedAt(Instant.parse("2026-09-01T09:59:30Z"));
        attemptRepository.saveAndFlush(attempt);

        var outcome = verificationService.verify(new RazorpayPaymentVerificationRequest(
                REQUEST_ID, ORDER_ID, paymentId, signature));

        assertEquals(200, outcome.httpStatus().value());
        assertEquals(EVENT_ID, outcome.response().recoveryEventId());
        assertEquals(Outcome.RECOVERED, auditRepository.findByEventId(EVENT_ID).orElseThrow().getOutcome());
        assertEquals(1, historyRepository.findByEventIdOrderByIdAsc(EVENT_ID).stream()
                .filter(entry -> entry.getIdempotencyKey() != null
                        && entry.getIdempotencyKey().contains(":transaction-recovery:"))
                .count());
    }

    @Test
    void manualStatusCheckReturnsClearNoPaymentResultAndCanFinalizeStoredSuccess() {
        AuditRecord record = auditRepository.findByEventId(EVENT_ID).orElseThrow();
        createLinkedOrder(record);

        var empty = statusService.check(EVENT_ID);
        assertEquals("no_payment_recorded", empty.status());
        assertEquals(false, empty.recovered());

        String paymentId = "pay_manual_status_fixture";
        String signature = verificationService.calculateSignature(ORDER_ID, paymentId,
                "deterministic_noncredential_fixture_secret");
        RazorpayTestCheckoutAttempt attempt = new RazorpayTestCheckoutAttempt();
        attempt.setInternalRequestId(REQUEST_ID);
        attempt.setRazorpayOrderId(ORDER_ID);
        attempt.setRazorpayPaymentId(paymentId);
        attempt.setRazorpaySignature(signature);
        attempt.setEventType(RazorpayCheckoutEventService.SUCCESS);
        attempt.setStatus(RazorpayCheckoutEventService.UNVERIFIED);
        attempt.setCreatedAt(Instant.now());
        attemptRepository.saveAndFlush(attempt);
        TransactionRecoveryPaymentLink staleLink = linkRepository.findByEventEventId(EVENT_ID).orElseThrow();
        staleLink.setStatus(RecoveryTransactionEligibilityService.ABANDONED);
        linkRepository.saveAndFlush(staleLink);

        var recovered = statusService.check(EVENT_ID);
        assertEquals("recovered", recovered.status());
        assertTrue(recovered.recovered());
        assertEquals(Outcome.RECOVERED, auditRepository.findByEventId(EVENT_ID).orElseThrow().getOutcome());
        assertEquals(1, historyRepository.findByEventIdOrderByIdAsc(EVENT_ID).stream()
                .filter(entry -> entry.getReason() != null
                        && entry.getReason().startsWith("CUSTOMER_INITIATED_RECOVERY_VERIFIED_TEST_MODE"))
                .count());
    }

    private RazorpayTestOrder createLinkedOrder(AuditRecord record) {
        Event event = eventRepository.findByEventId(EVENT_ID).orElseThrow();
        long paise = RazorpayAmountConverter.toPaise(record.getAmount());
        RazorpayTestOrder order = new RazorpayTestOrder();
        order.setInternalRequestId(REQUEST_ID);
        order.setRazorpayOrderId(ORDER_ID);
        order.setReceipt("recoverai_txn_recovery_test");
        order.setAmountInr(record.getAmount());
        order.setAmountPaise(paise);
        order.setCurrency("INR");
        order.setStatus(RazorpayCheckoutEventService.UNVERIFIED);
        order.setMode("test");
        order.setCreatedAt(Instant.now());
        orderRepository.saveAndFlush(order);

        TransactionRecoveryPaymentLink link = new TransactionRecoveryPaymentLink();
        link.setEvent(event);
        link.setInternalRequestId(REQUEST_ID);
        link.setRazorpayOrderId(ORDER_ID);
        link.setAmountInr(record.getAmount());
        link.setAmountPaise(paise);
        link.setCurrency("INR");
        link.setMode("test");
        link.setPurpose(TransactionRecoveryCheckoutService.PURPOSE);
        link.setRecoveryAction(RecoveryCheckoutAction.RESUME_PAYMENT);
        link.setStatus(RazorpayCheckoutEventService.UNVERIFIED);
        link.setCreatedAt(Instant.now());
        linkRepository.saveAndFlush(link);
        assertTrue(linkRepository.existsByEventEventId(EVENT_ID));
        return order;
    }
}
