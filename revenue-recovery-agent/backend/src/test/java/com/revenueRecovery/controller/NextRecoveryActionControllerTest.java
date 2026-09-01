package com.revenueRecovery.controller;

import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:next-action-testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class NextRecoveryActionControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired AuditRecordRepository auditRecordRepository;
    @Autowired EventRepository eventRepository;
    @Autowired AuditHistoryRepository historyRepository;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired RazorpayTestCheckoutAttemptRepository checkoutRepository;
    @Autowired RecoveryPaymentLinkRepository linkRepository;

    @Test
    void endpointReturnsSafePolicyDecisionsForPersistedBenchmarkCases() throws Exception {
        runBatch();

        mockMvc.perform(get("/api/transactions/does-not-exist/next-action"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found"));

        assertDecision("TXN10001", "STOPPED_BY_POLICY", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10006", "CUSTOMER_RECOVERY_CHECKOUT", "OPEN_RECOVERY_CHECKOUT", true,
                "razorpay_test_recovery");
        assertDecision("TXN10010", "ESCALATE_TO_MERCHANT", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10029", "VERIFY_PAYMENT_STATUS", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10048", "ESCALATE_TO_MERCHANT", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10051", "ESCALATE_MANDATE_RENEWAL", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10021", "VERIFY_PAYMENT_STATUS", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10059", "CUSTOMER_RECOVERY_CHECKOUT", "OPEN_RECOVERY_CHECKOUT", true,
                "razorpay_test_recovery");
        // The deterministic baseline recovered every seeded checkout-abandoned and
        // insufficient-balance row, so the terminal guard correctly takes priority.
        assertDecision("TXN10045", "ALREADY_RECOVERED", "NONE", false, "synthetic_benchmark");
        assertDecision("TXN10037", "ALREADY_RECOVERED", "NONE", false, "synthetic_benchmark");
    }

    @Test
    void dedicatedDemoRetainsItsSeparateCheckoutDecision() throws Exception {
        mockMvc.perform(get("/api/transactions/TXN_DEMO_RECOVERY_001/next-action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_outcome").value("not_recovered"))
                .andExpect(jsonPath("$.lifecycle_state").value("awaiting_customer_payment"))
                .andExpect(jsonPath("$.recommended_action").value("SEND_RECOVERY_LINK"))
                .andExpect(jsonPath("$.button_label").value("Open Test Mode Recovery Checkout"))
                .andExpect(jsonPath("$.is_action_allowed").value(true))
                .andExpect(jsonPath("$.action_type").value("OPEN_TEST_MODE_RECOVERY_CHECKOUT"))
                .andExpect(jsonPath("$.mode").value("razorpay_test_demo"));
    }

    @Test
    void lookupIsReadOnlyAndBaselineMetricsRemainExact() throws Exception {
        runBatch();
        long records = auditRecordRepository.count();
        long events = eventRepository.count();
        long history = historyRepository.count();
        long orders = orderRepository.count();
        long checkouts = checkoutRepository.count();
        long links = linkRepository.count();

        String body = mockMvc.perform(get("/api/transactions/TXN10059/next-action"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(records, auditRecordRepository.count());
        assertEquals(events, eventRepository.count());
        assertEquals(history, historyRepository.count());
        assertEquals(orders, orderRepository.count());
        assertEquals(checkouts, checkoutRepository.count());
        assertEquals(links, linkRepository.count());
        assertFalse(body.toLowerCase().contains("key_secret"));
        assertFalse(body.toLowerCase().contains("razorpay_signature"));

        mockMvc.perform(get("/api/batch-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(65))
                .andExpect(jsonPath("$.total_at_risk").value(191209.00))
                .andExpect(jsonPath("$.total_recovered").value(95647.00))
                .andExpect(jsonPath("$.recovery_rate").value(0.5002));
    }

    private void runBatch() throws Exception {
        mockMvc.perform(post("/api/batch/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(65));
    }

    private void assertDecision(String eventId, String recommendation, String actionType, boolean allowed,
            String mode)
            throws Exception {
        mockMvc.perform(get("/api/transactions/{eventId}/next-action", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(eventId))
                .andExpect(jsonPath("$.recommended_action").value(recommendation))
                .andExpect(jsonPath("$.action_type").value(actionType))
                .andExpect(jsonPath("$.is_action_allowed").value(allowed))
                .andExpect(jsonPath("$.mode").value(mode));
    }
}
