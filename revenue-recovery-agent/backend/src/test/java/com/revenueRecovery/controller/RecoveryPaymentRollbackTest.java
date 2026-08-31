package com.revenueRecovery.controller;

import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.RecoveryPaymentLink;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.service.RecoveryDemoAuditService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recovery-rollback-testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "razorpay.key-id=rzp_test_rollback_placeholder",
        "razorpay.key-secret=rollback-test-secret"
})
@AutoConfigureMockMvc
class RecoveryPaymentRollbackTest {
    private static final String EVENT_ID = "TXN_DEMO_RECOVERY_001";
    private static final String INTERNAL_ID = "req_rollback";
    private static final String ORDER_ID = "order_rollback";
    private static final String PAYMENT_ID = "pay_rollback";
    private static final String SECRET = "rollback-test-secret";

    @Autowired MockMvc mockMvc;
    @Autowired RecoveryDemoCaseRepository caseRepository;
    @Autowired RecoveryPaymentLinkRepository linkRepository;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired RazorpayTestCheckoutAttemptRepository attemptRepository;
    @Autowired AuditHistoryRepository historyRepository;
    @MockBean RecoveryDemoAuditService auditService;

    @BeforeEach
    void seed() throws Exception {
        attemptRepository.deleteAll();
        linkRepository.deleteAll();
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        caseRepository.deleteAll();

        RecoveryDemoCase recoveryCase = new RecoveryDemoCase();
        recoveryCase.setEventId(EVENT_ID);
        recoveryCase.setCaseType("payment_degradation");
        recoveryCase.setRootCause(RootCause.CHECKOUT_ABANDONED);
        recoveryCase.setAmount(new BigDecimal("500.00"));
        recoveryCase.setCurrency("INR");
        recoveryCase.setPolicyAction(ActionTaken.SEND_RECOVERY_LINK);
        recoveryCase.setOutcome(Outcome.NOT_RECOVERED);
        recoveryCase.setRecoveryStatus("awaiting_customer_payment");
        recoveryCase.setDemoOnly(true);
        recoveryCase.setCreatedAt(Instant.now());
        caseRepository.saveAndFlush(recoveryCase);

        RazorpayTestOrder order = new RazorpayTestOrder();
        order.setInternalRequestId(INTERNAL_ID);
        order.setRazorpayOrderId(ORDER_ID);
        order.setReceipt("recoverai_rollback");
        order.setAmountInr(new BigDecimal("500.00"));
        order.setAmountPaise(50000L);
        order.setCurrency("INR");
        order.setStatus("created");
        order.setMode("test");
        order.setCreatedAt(Instant.now());
        orderRepository.saveAndFlush(order);

        RecoveryPaymentLink link = new RecoveryPaymentLink();
        link.setRecoveryCase(recoveryCase);
        link.setInternalRequestId(INTERNAL_ID);
        link.setRazorpayOrderId(ORDER_ID);
        link.setAmountInr(new BigDecimal("500.00"));
        link.setAmountPaise(50000L);
        link.setCurrency("INR");
        link.setMode("test");
        link.setPurpose(RecoveryPaymentLinkService.PURPOSE);
        link.setStatus("client_reported_unverified");
        link.setCreatedAt(Instant.now());
        linkRepository.saveAndFlush(link);

        String signature = signature();
        RazorpayTestCheckoutAttempt attempt = new RazorpayTestCheckoutAttempt();
        attempt.setInternalRequestId(INTERNAL_ID);
        attempt.setRazorpayOrderId(ORDER_ID);
        attempt.setRazorpayPaymentId(PAYMENT_ID);
        attempt.setRazorpaySignature(signature);
        attempt.setEventType("checkout_success");
        attempt.setStatus("client_reported_unverified");
        attempt.setCreatedAt(Instant.now());
        attemptRepository.saveAndFlush(attempt);

        when(auditService.appendVerifiedRecovery(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("forced audit persistence failure"));
    }

    @Test
    void auditFailureRollsBackPaymentLinkCaseAndVerificationTogether() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"internal_request_id":"%s","razorpay_order_id":"%s",
                                 "razorpay_payment_id":"%s","razorpay_signature":"%s"}
                                """.formatted(INTERNAL_ID, ORDER_ID, PAYMENT_ID, signature())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));

        assertEquals(Outcome.NOT_RECOVERED,
                caseRepository.findByEventId(EVENT_ID).orElseThrow().getOutcome());
        RecoveryPaymentLink link = linkRepository.findByRecoveryCaseEventId(EVENT_ID).orElseThrow();
        assertEquals("client_reported_unverified", link.getStatus());
        assertNull(link.getRazorpayPaymentId());
        RazorpayTestCheckoutAttempt attempt = attemptRepository
                .findByInternalRequestIdAndRazorpayOrderIdAndRazorpayPaymentId(
                        INTERNAL_ID, ORDER_ID, PAYMENT_ID).orElseThrow();
        assertEquals("client_reported_unverified", attempt.getStatus());
        assertEquals("created", orderRepository.findByInternalRequestId(INTERNAL_ID).orElseThrow().getStatus());
        assertEquals(0, historyRepository.countByEventId(EVENT_ID));
    }

    private String signature() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(
                (ORDER_ID + "|" + PAYMENT_ID).getBytes(StandardCharsets.UTF_8)));
    }
}
