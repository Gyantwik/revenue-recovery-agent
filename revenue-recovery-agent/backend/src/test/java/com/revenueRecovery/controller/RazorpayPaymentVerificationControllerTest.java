package com.revenueRecovery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:razorpay-verification-testdb",
        "razorpay.key-id=rzp_test_verification_placeholder",
        "razorpay.key-secret=verification-unit-secret"
})
@AutoConfigureMockMvc
@Transactional
class RazorpayPaymentVerificationControllerTest {
    private static final String INTERNAL_ID = "req_verify_test";
    private static final String ORDER_ID = "order_verify_test";
    private static final String PAYMENT_ID = "pay_verify_test";
    private static final String SECRET = "verification-unit-secret";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired RazorpayTestCheckoutAttemptRepository attemptRepository;
    @Autowired AuditRecordRepository auditRecordRepository;
    @Autowired AuditHistoryRepository auditHistoryRepository;

    @BeforeEach
    void createOrder() {
        RazorpayTestOrder order = new RazorpayTestOrder();
        order.setInternalRequestId(INTERNAL_ID);
        order.setRazorpayOrderId(ORDER_ID);
        order.setReceipt("recoverai_verify_test");
        order.setAmountInr(new BigDecimal("500.00"));
        order.setAmountPaise(50000L);
        order.setCurrency("INR");
        order.setStatus("created");
        order.setMode("test");
        order.setCreatedAt(Instant.now());
        orderRepository.save(order);
    }

    @Test
    void validSignaturePersistsVerifiedStatusAndTimestampWithoutExposingSignature() throws Exception {
        String signature = signature(ORDER_ID, PAYMENT_ID);
        saveSuccess(PAYMENT_ID, signature);

        String body = verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, signature, 200)
                .andExpect(jsonPath("$.verification_status").value("verified_test_payment"))
                .andExpect(jsonPath("$.mode").value("test"))
                .andExpect(jsonPath("$.verified_at").exists())
                .andReturn().getResponse().getContentAsString();

        RazorpayTestCheckoutAttempt stored = attemptRepository.findAll().get(0);
        assertEquals("verified_test_payment", stored.getStatus());
        assertNotNull(stored.getVerifiedAt());
        assertNull(stored.getRazorpaySignature());
        assertFalse(body.contains(signature));
        assertFalse(body.contains(SECRET));
    }

    @Test
    void tamperedSignaturePersistsTerminalSafeFailure() throws Exception {
        String tampered = "0".repeat(64);
        saveSuccess(PAYMENT_ID, tampered);

        String body = verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, tampered, 422)
                .andExpect(jsonPath("$.verification_status").value("verification_failed"))
                .andExpect(jsonPath("$.verified_at").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        RazorpayTestCheckoutAttempt stored = attemptRepository.findAll().get(0);
        assertEquals("verification_failed", stored.getStatus());
        assertEquals("SIGNATURE_MISMATCH", stored.getVerificationFailureCode());
        assertNull(stored.getVerifiedAt());
        assertNull(stored.getRazorpaySignature());
        assertFalse(body.contains(tampered));
    }

    @Test
    void suppliedSignatureMustMatchPersistedCallbackSignature() throws Exception {
        String valid = signature(ORDER_ID, PAYMENT_ID);
        saveSuccess(PAYMENT_ID, "f".repeat(64));

        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, valid, 422)
                .andExpect(jsonPath("$.verification_status").value("verification_failed"));
    }

    @Test
    void missingFieldsUnknownRequestAndOrderMismatchReturnSafeErrors() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid verification request"));

        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("req_missing", ORDER_ID, PAYMENT_ID, "0".repeat(64))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Test order not found"));

        mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(INTERNAL_ID, "order_client_alternate", PAYMENT_ID,
                                "0".repeat(64))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Order mismatch"));
    }

    @Test
    void missingSuccessAndDismissalCannotBeVerified() throws Exception {
        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, "0".repeat(64), 409)
                .andExpect(jsonPath("$.error").value("Checkout success not recorded"));

        RazorpayTestCheckoutAttempt dismissed = new RazorpayTestCheckoutAttempt();
        dismissed.setInternalRequestId(INTERNAL_ID);
        dismissed.setRazorpayOrderId(ORDER_ID);
        dismissed.setEventType("checkout_failed_or_dismissed");
        dismissed.setReason("user_cancelled_or_test_failure");
        dismissed.setStatus("client_reported_unverified");
        dismissed.setCreatedAt(Instant.now());
        attemptRepository.save(dismissed);

        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, "0".repeat(64), 409)
                .andExpect(jsonPath("$.error").value("Checkout success not recorded"));
    }

    @Test
    void duplicateVerificationAndDifferentPaymentAreRejected() throws Exception {
        String firstSignature = signature(ORDER_ID, PAYMENT_ID);
        saveSuccess(PAYMENT_ID, firstSignature);
        String otherPayment = "pay_verify_other";
        saveSuccess(otherPayment, signature(ORDER_ID, otherPayment));

        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, firstSignature, 200);
        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, firstSignature, 409)
                .andExpect(jsonPath("$.error").value("Verification already finalized"));
        verify(INTERNAL_ID, ORDER_ID, otherPayment, signature(ORDER_ID, otherPayment), 409)
                .andExpect(jsonPath("$.message").value(
                        "This Test Mode order is already verified with a different payment ID."));
    }

    @Test
    void failedVerificationCannotLaterBeRetried() throws Exception {
        String tampered = "0".repeat(64);
        saveSuccess(PAYMENT_ID, tampered);
        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, tampered, 422);
        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, signature(ORDER_ID, PAYMENT_ID), 409)
                .andExpect(jsonPath("$.message").value(
                        "Verification previously failed and this terminal attempt cannot be retried."));
    }

    @Test
    void verificationDoesNotChangeRecoveryMetricsRecordsOrHistory() throws Exception {
        mockMvc.perform(post("/api/batch/run")).andExpect(status().isOk());
        JsonNode before = objectMapper.readTree(mockMvc.perform(get("/api/batch-summary"))
                .andReturn().getResponse().getContentAsString());
        long recordsBefore = auditRecordRepository.count();
        long historyBefore = auditHistoryRepository.count();

        String signature = signature(ORDER_ID, PAYMENT_ID);
        saveSuccess(PAYMENT_ID, signature);
        verify(INTERNAL_ID, ORDER_ID, PAYMENT_ID, signature, 200);

        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/batch-summary"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(before, after);
        assertEquals(80, after.get("total_cases").asInt());
        assertEquals(0, new BigDecimal("191209.00").compareTo(after.get("total_at_risk").decimalValue()));
        assertEquals(recordsBefore, auditRecordRepository.count());
        assertEquals(historyBefore, auditHistoryRepository.count());
    }

    private org.springframework.test.web.servlet.ResultActions verify(String internalId, String orderId,
            String paymentId, String signature, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/razorpay/test/verify-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(internalId, orderId, paymentId, signature)))
                .andExpect(status().is(expectedStatus));
    }

    private String requestBody(String internalId, String orderId, String paymentId, String signature) {
        return """
                {"internal_request_id":"%s","razorpay_order_id":"%s",
                 "razorpay_payment_id":"%s","razorpay_signature":"%s"}
                """.formatted(internalId, orderId, paymentId, signature);
    }

    private void saveSuccess(String paymentId, String signature) {
        RazorpayTestCheckoutAttempt attempt = new RazorpayTestCheckoutAttempt();
        attempt.setInternalRequestId(INTERNAL_ID);
        attempt.setRazorpayOrderId(ORDER_ID);
        attempt.setRazorpayPaymentId(paymentId);
        attempt.setRazorpaySignature(signature);
        attempt.setEventType("checkout_success");
        attempt.setStatus("client_reported_unverified");
        attempt.setCreatedAt(Instant.now());
        attemptRepository.save(attempt);
        orderRepository.findByInternalRequestId(INTERNAL_ID).orElseThrow()
                .setStatus("client_reported_unverified");
    }

    private String signature(String orderId, String paymentId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(
                (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }
}
