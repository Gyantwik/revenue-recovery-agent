package com.revenueRecovery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:razorpay-checkout-testdb",
        "razorpay.key-id=rzp_test_safe_checkout_placeholder",
        "razorpay.key-secret=safe-checkout-secret-placeholder"
})
@AutoConfigureMockMvc
@Transactional
class RazorpayTestCheckoutControllerTest {
    private static final String INTERNAL_ID = "req_checkout_test";
    private static final String ORDER_ID = "order_checkout_test";
    private static final String PAYMENT_ID = "pay_checkout_test";
    private static final String SIGNATURE = "checkout_signature_not_verified";
    private static final String SECRET = "safe-checkout-secret-placeholder";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired RazorpayTestCheckoutAttemptRepository attemptRepository;
    @Autowired AuditRecordRepository auditRecordRepository;
    @Autowired AuditHistoryRepository auditHistoryRepository;

    @BeforeEach
    void createMappedOrder() {
        RazorpayTestOrder order = new RazorpayTestOrder();
        order.setInternalRequestId(INTERNAL_ID);
        order.setRazorpayOrderId(ORDER_ID);
        order.setReceipt("recoverai_checkout_test");
        order.setAmountInr(new BigDecimal("500.00"));
        order.setAmountPaise(50000L);
        order.setCurrency("INR");
        order.setStatus("created");
        order.setMode("test");
        order.setCreatedAt(Instant.now());
        orderRepository.save(order);
    }

    @Test
    void configReturnsOnlySafeTestKeyIdAndMode() throws Exception {
        String body = mockMvc.perform(get("/api/razorpay/test/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key_id").value("rzp_test_safe_checkout_placeholder"))
                .andExpect(jsonPath("$.mode").value("test"))
                .andReturn().getResponse().getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertEquals(2, response.size());
        assertFalse(body.contains(SECRET));
        assertFalse(body.toLowerCase().contains("secret"));
    }

    @Test
    void acceptsUnverifiedSuccessWithoutExposingSignature() throws Exception {
        String body = mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successBody(ORDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_type").value("checkout_success"))
                .andExpect(jsonPath("$.status").value("client_reported_unverified"))
                .andExpect(jsonPath("$.razorpay_payment_id").value(PAYMENT_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(SIGNATURE));
        assertEquals(1, attemptRepository.count());
        assertEquals("client_reported_unverified", orderRepository.findByInternalRequestId(INTERNAL_ID)
                .orElseThrow().getStatus());
    }

    @Test
    void acceptsUnverifiedDismissal() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"internal_request_id":"req_checkout_test",
                                 "razorpay_order_id":"order_checkout_test",
                                 "event_type":"checkout_failed_or_dismissed",
                                 "reason":"user_cancelled_or_test_failure"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("client_reported_unverified"))
                .andExpect(jsonPath("$.razorpay_payment_id").doesNotExist());
        assertEquals(1, attemptRepository.count());
    }

    @Test
    void rejectsMismatchedOrder() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successBody("order_different")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid checkout event"));
        assertEquals(0, attemptRepository.count());
    }

    @Test
    void rejectsUnknownInternalRequest() throws Exception {
        String body = successBody(ORDER_ID).replace(INTERNAL_ID, "req_missing");
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Test order not found"));
        assertEquals(0, attemptRepository.count());
    }

    @Test
    void rejectsMissingSuccessIdentifiersAndUnknownEvent() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"internal_request_id":"req_checkout_test",
                                 "razorpay_order_id":"order_checkout_test",
                                 "event_type":"checkout_success"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Payment ID and signature are required for checkout_success."));

        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"internal_request_id":"req_checkout_test",
                                 "razorpay_order_id":"order_checkout_test",
                                 "event_type":"payment_verified"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown checkout event type."));
        assertEquals(0, attemptRepository.count());
    }

    @Test
    void rejectsDuplicateClientReportedSuccess() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON).content(successBody(ORDER_ID)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON).content(successBody(ORDER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Duplicate checkout event"));
        assertEquals(1, attemptRepository.count());
    }

    @Test
    void intakeCreatesOneLiveDashboardTransactionWithoutSyntheticAuditHistory() throws Exception {
        long recordsBefore = auditRecordRepository.count();
        long historyBefore = auditHistoryRepository.count();
        JsonNode summaryBefore = objectMapper.readTree(mockMvc.perform(get("/api/batch-summary"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/razorpay/test/checkout-events")
                        .contentType(MediaType.APPLICATION_JSON).content(successBody(ORDER_ID)))
                .andExpect(status().isOk());

        JsonNode summaryAfter = objectMapper.readTree(mockMvc.perform(get("/api/batch-summary"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(summaryBefore.get("total_cases").asLong() + 1, summaryAfter.get("total_cases").asLong());
        assertEquals(recordsBefore + 1, auditRecordRepository.count());
        assertEquals(historyBefore, auditHistoryRepository.count());
        assertEquals("live", auditRecordRepository.findByGatewayOrderId(ORDER_ID).orElseThrow().getSource().toJson());
    }

    private String successBody(String orderId) {
        return """
                {"internal_request_id":"%s","razorpay_order_id":"%s",
                 "razorpay_payment_id":"%s","razorpay_signature":"%s",
                 "event_type":"checkout_success"}
                """.formatted(INTERNAL_ID, orderId, PAYMENT_ID, SIGNATURE);
    }
}
