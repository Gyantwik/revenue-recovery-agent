package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.RecoveryPaymentLink;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.service.PolicyEngine;
import com.revenueRecovery.service.RazorpayOrderService;
import com.revenueRecovery.service.RecoveryDemoCaseSeeder;
import com.revenueRecovery.service.RecoveryDemoAuditService;
import com.revenueRecovery.service.RecoveryPaymentFinalizationService;
import com.revenueRecovery.service.RecoveryPaymentLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recovery-payment-testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "razorpay.key-id=rzp_test_recovery_placeholder",
        "razorpay.key-secret=recovery-test-secret"
})
@AutoConfigureMockMvc
class RecoveryPaymentControllerTest {
    private static final String EVENT_ID = RecoveryDemoCaseSeeder.DEMO_EVENT_ID;
    private static final String INTERNAL_ID = "req_recovery_test";
    private static final String ORDER_ID = "order_recovery_test";
    private static final String PAYMENT_ID = "pay_recovery_test";
    private static final String SECRET = "recovery-test-secret";

    @Autowired MockMvc mockMvc;
    @Autowired RecoveryDemoCaseRepository caseRepository;
    @Autowired RecoveryPaymentLinkRepository linkRepository;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired RazorpayTestCheckoutAttemptRepository attemptRepository;
    @Autowired AuditHistoryRepository historyRepository;
    @Autowired AuditRecordRepository auditRecordRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PolicyEngine policyEngine;
    @MockBean RazorpayOrderService orderService;

    @BeforeEach
    void resetData() {
        attemptRepository.deleteAll();
        linkRepository.deleteAll();
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        auditRecordRepository.deleteAll();
        eventRepository.deleteAll();
        caseRepository.deleteAll();
        seedCase(EVENT_ID, RootCause.CHECKOUT_ABANDONED, Outcome.NOT_RECOVERED);
        when(orderService.createRecoveryTestOrder(any(), eq("INR"), eq(EVENT_ID)))
                .thenReturn(new RazorpayTestOrderResponse(
                        INTERNAL_ID, ORDER_ID, 50000, "INR", "recoverai_recovery_test", "created", "test"));
    }

    @Test
    void demoCaseIsSeparateFromTheUnchangedBaseline() throws Exception {
        mockMvc.perform(get("/api/recovery/test-mode/demo-case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(EVENT_ID))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.failure_root_cause").value("Checkout Abandoned"))
                .andExpect(jsonPath("$.policy_action").value("Send Recovery Link"))
                .andExpect(jsonPath("$.demo_only").value(true));

        mockMvc.perform(post("/api/batch/run")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(80))
                .andExpect(jsonPath("$.total_at_risk").value(191209.00))
                ;
        assertEquals(1, caseRepository.count());
        assertFalse(auditRecordRepository.findByEventId(EVENT_ID).isPresent());
    }

    @Test
    void linkedOrderUsesPersistedAmountAndOnlyOneLinkCanExist() throws Exception {
        mockMvc.perform(post("/api/recovery/{eventId}/razorpay-test-order", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":999999,\"currency\":\"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.link_status").value("order_created"))
                .andExpect(jsonPath("$.recovery_status").value("awaiting_customer_payment"));

        RecoveryDemoCase stored = caseRepository.findByEventId(EVENT_ID).orElseThrow();
        assertEquals(Outcome.NOT_RECOVERED, stored.getOutcome());
        assertEquals(1, linkRepository.count());
        mockMvc.perform(post("/api/recovery/{eventId}/razorpay-test-order", EVENT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A recovery payment link already exists for this case."));
    }

    @Test
    void checkoutIntakeAndInvalidSignatureNeverRecoverTheCase() throws Exception {
        seedOrderAndLink();
        String tampered = "0".repeat(64);
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(PAYMENT_ID, tampered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("client_reported_unverified"));
        assertNotRecovered();

        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationBody(PAYMENT_ID, tampered)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.verification_status").value("verification_failed"))
                .andExpect(jsonPath("$.link_status").value("verification_failed"));
        assertNotRecovered();
        assertEquals(0, historyRepository.countByEventId(EVENT_ID));
    }

    @Test
    void validSignatureAtomicallyRecoversDemoAndWritesExactlyOneSafeAudit() throws Exception {
        mockMvc.perform(post("/api/batch/run")).andExpect(status().isOk());
        seedOrderAndLink();
        String signature = signature(ORDER_ID, PAYMENT_ID);
        saveSuccessAttempt(PAYMENT_ID, signature);
        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationBody(PAYMENT_ID, signature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_status").value("verified_test_payment"))
                .andExpect(jsonPath("$.recovery_event_id").value(EVENT_ID))
                .andExpect(jsonPath("$.recovery_status").value("recovered"))
                .andExpect(jsonPath("$.link_status").value("recovered_by_verified_test_payment"));

        RecoveryDemoCase recovered = caseRepository.findByEventId(EVENT_ID).orElseThrow();
        RecoveryPaymentLink link = linkRepository.findByRecoveryCaseEventId(EVENT_ID).orElseThrow();
        assertEquals(Outcome.RECOVERED, recovered.getOutcome());
        assertEquals("recovered", recovered.getRecoveryStatus());
        assertEquals("recovered_by_verified_test_payment", link.getStatus());
        assertEquals(PAYMENT_ID, link.getRazorpayPaymentId());
        assertEquals(1, historyRepository.countByEventId(EVENT_ID));
        String reason = historyRepository.findByEventIdOrderByIdAsc(EVENT_ID).get(0).getReason();
        assertEquals(true, reason.contains(RecoveryDemoAuditService.ACTION_RESULT));
        assertEquals(true, reason.contains("mode=TEST"));
        assertFalse(reason.toLowerCase().contains("secret"));
        assertFalse(reason.contains(signature));
        assertFalse(auditRecordRepository.findByEventId(EVENT_ID).isPresent());
        mockMvc.perform(get("/api/batch-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(80))
                .andExpect(jsonPath("$.total_at_risk").value(191209.00))
                ;

        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationBody(PAYMENT_ID, signature)))
                .andExpect(status().isConflict());
        assertEquals(1, historyRepository.countByEventId(EVENT_ID));
    }

    @Test
    void differentPaymentAndConcurrentDuplicateCannotRecoverTwice() throws Exception {
        seedOrderAndLink();
        String signature = signature(ORDER_ID, PAYMENT_ID);
        saveSuccessAttempt(PAYMENT_ID, signature);
        saveSuccessAttempt("pay_recovery_other", signature(ORDER_ID, "pay_recovery_other"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> call = () -> mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationBody(PAYMENT_ID, signature)))
                .andReturn().getResponse().getStatus();
        Future<Integer> first = executor.submit(call);
        Future<Integer> second = executor.submit(call);
        int firstStatus = first.get();
        int secondStatus = second.get();
        executor.shutdownNow();
        assertEquals(1, java.util.List.of(firstStatus, secondStatus).stream()
                .filter(status -> status == 200).count());
        assertEquals(1, java.util.List.of(firstStatus, secondStatus).stream()
                .filter(status -> status == 409).count());
        assertEquals(1, historyRepository.countByEventId(EVENT_ID));

        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationBody("pay_recovery_other",
                                signature(ORDER_ID, "pay_recovery_other"))))
                .andExpect(status().isConflict());
        assertEquals(1, historyRepository.countByEventId(EVENT_ID));
    }

    private RecoveryDemoCase seedCase(String eventId, RootCause cause, Outcome outcome) {
        RecoveryDemoCase recoveryCase = new RecoveryDemoCase();
        recoveryCase.setEventId(eventId);
        recoveryCase.setCaseType("payment_degradation");
        recoveryCase.setRootCause(cause);
        recoveryCase.setAmount(new BigDecimal("500.00"));
        recoveryCase.setCurrency("INR");
        recoveryCase.setPolicyAction(policyEngine.decide(cause).getActionTaken());
        recoveryCase.setOutcome(outcome);
        recoveryCase.setRecoveryStatus(outcome == Outcome.RECOVERED ? "recovered" : "awaiting_customer_payment");
        recoveryCase.setDemoOnly(true);
        recoveryCase.setCreatedAt(Instant.parse("2026-08-31T00:00:00Z"));
        return caseRepository.saveAndFlush(recoveryCase);
    }

    private void seedOrderAndLink() {
        RazorpayTestOrder order = new RazorpayTestOrder();
        order.setInternalRequestId(INTERNAL_ID);
        order.setRazorpayOrderId(ORDER_ID);
        order.setReceipt("recoverai_recovery_test");
        order.setAmountInr(new BigDecimal("500.00"));
        order.setAmountPaise(50000L);
        order.setCurrency("INR");
        order.setStatus("created");
        order.setMode("test");
        order.setCreatedAt(Instant.now());
        orderRepository.saveAndFlush(order);

        RecoveryPaymentLink link = new RecoveryPaymentLink();
        link.setRecoveryCase(caseRepository.findByEventId(EVENT_ID).orElseThrow());
        link.setInternalRequestId(INTERNAL_ID);
        link.setRazorpayOrderId(ORDER_ID);
        link.setAmountInr(new BigDecimal("500.00"));
        link.setAmountPaise(50000L);
        link.setCurrency("INR");
        link.setMode("test");
        link.setPurpose(RecoveryPaymentLinkService.PURPOSE);
        link.setStatus(RecoveryPaymentLinkService.ORDER_CREATED);
        link.setCreatedAt(Instant.now());
        linkRepository.saveAndFlush(link);
    }

    private void saveSuccessAttempt(String paymentId, String signature) {
        RazorpayTestCheckoutAttempt attempt = new RazorpayTestCheckoutAttempt();
        attempt.setInternalRequestId(INTERNAL_ID);
        attempt.setRazorpayOrderId(ORDER_ID);
        attempt.setRazorpayPaymentId(paymentId);
        attempt.setRazorpaySignature(signature);
        attempt.setEventType("checkout_success");
        attempt.setStatus("client_reported_unverified");
        attempt.setCreatedAt(Instant.now());
        attemptRepository.saveAndFlush(attempt);
        RecoveryPaymentLink link = linkRepository.findByRecoveryCaseEventId(EVENT_ID).orElseThrow();
        link.setStatus("client_reported_unverified");
        linkRepository.saveAndFlush(link);
    }

    private void assertNotRecovered() {
        RecoveryDemoCase recoveryCase = caseRepository.findByEventId(EVENT_ID).orElseThrow();
        assertEquals(Outcome.NOT_RECOVERED, recoveryCase.getOutcome());
        assertEquals("awaiting_customer_payment", recoveryCase.getRecoveryStatus());
        assertNull(recoveryCase.getRecoveredAt());
    }

    private String checkoutBody(String paymentId, String signature) {
        return verificationBody(paymentId, signature).replaceFirst("}$",
                ",\"event_type\":\"checkout_success\"}");
    }

    private String verificationBody(String paymentId, String signature) {
        return """
                {"internal_request_id":"%s","razorpay_order_id":"%s",
                 "razorpay_payment_id":"%s","razorpay_signature":"%s"}
                """.formatted(INTERNAL_ID, ORDER_ID, paymentId, signature);
    }

    private String signature(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(
                (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }
}
