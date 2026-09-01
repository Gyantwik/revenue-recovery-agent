package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.service.BatchService;
import com.revenueRecovery.service.RazorpayAmountConverter;
import com.revenueRecovery.service.RazorpayOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transaction-recovery-controller;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class TransactionRecoveryCheckoutControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired BatchService batchService;
    @Autowired AuditRecordRepository auditRepository;
    @MockBean RazorpayOrderService orderService;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void seedAndMockOrderCreation() throws Exception {
        batchService.runBatch();
        when(orderService.createRecoveryTestOrder(any(BigDecimal.class), eq("INR"), any(String.class)))
                .thenAnswer(invocation -> {
                    BigDecimal amount = invocation.getArgument(0);
                    int id = sequence.incrementAndGet();
                    return new RazorpayTestOrderResponse("req_recovery_" + id, "order_recovery_" + id,
                            RazorpayAmountConverter.toPaise(amount), "INR", "recoverai_recovery_" + id,
                            "created", "test");
                });
    }

    @Test
    void exactEligibleCategoriesCreateServerDerivedOrdersWithoutRequestBody() throws Exception {
        makeNotRecovered("TXN10044");
        makeNotRecovered("TXN10036");
        makeNotRecovered("TXN10014");
        AuditRecord bank = auditRepository.findByEventId("TXN10014").orElseThrow();
        bank.setAttemptNumber(bank.getMaxAttemptsAllowed());
        bank.setLifecycleState(LifecycleState.RETRY_EXHAUSTED);
        auditRepository.saveAndFlush(bank);
        assertCreated("TXN10044", 95000, "RESUME_PAYMENT");
        assertCreated("TXN10036", 110000, "CHOOSE_ANOTHER_PAYMENT_METHOD");
        assertCreated("TXN10006", 55000, "TRY_PAYMENT_AGAIN_SECURELY");
        assertCreated("TXN10014", 140000, "TRY_PAYMENT_AGAIN");

        AuditRecord mandate = auditRepository.findByEventId("TXN10056").orElseThrow();
        mandate.setOutcome(Outcome.NOT_RECOVERED);
        mandate.setLifecycleState(LifecycleState.RETRY_EXHAUSTED);
        auditRepository.saveAndFlush(mandate);
        assertCreated("TXN10056", 59900, "PAY_MANUALLY");
    }

    @Test
    void blockedCategoriesReturnPolicyGuidanceAndNeverCreateOrder() throws Exception {
        makeNotRecovered("TXN10032");
        assertBlocked("TXN10032", "Verify Payment Status First");
        assertBlocked("TXN10022", "Verify Previous Payment First");
        assertBlocked("TXN10001", "No Automatic Recovery Allowed");
        assertBlocked("TXN10048", "Merchant Review Required");
        assertBlocked("TXN10010", "Manual Review Required");
        assertBlocked("TXN10051", "Escalated for Review");
        assertBlocked("TXN10043", "Already Recovered");
    }

    @Test
    void scheduledRetryAndDuplicateLinkAreBlockedAndCreationDoesNotRecover() throws Exception {
        AuditRecord bank = auditRepository.findByEventId("TXN10014").orElseThrow();
        bank.setOutcome(Outcome.NOT_RECOVERED);
        bank.setRecoveredAmount(BigDecimal.ZERO.setScale(2));
        bank.setAttemptNumber(1);
        bank.setLifecycleState(LifecycleState.RETRY_SCHEDULED);
        auditRepository.saveAndFlush(bank);
        assertBlocked("TXN10014", "Retry Scheduled");

        makeNotRecovered("TXN10044");
        assertCreated("TXN10044", 95000, "RESUME_PAYMENT");
        mockMvc.perform(post("/api/transactions/TXN10044/recovery-checkout"))
                .andExpect(status().isConflict());
        AuditRecord unchanged = auditRepository.findByEventId("TXN10044").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Outcome.NOT_RECOVERED, unchanged.getOutcome());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                unchanged.getRecoveredAmount().compareTo(BigDecimal.ZERO));
    }

    private void assertCreated(String eventId, long amountPaise, String action) throws Exception {
        mockMvc.perform(post("/api/transactions/{eventId}/recovery-checkout", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(eventId))
                .andExpect(jsonPath("$.amount").value(amountPaise))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.recovery_action").value(action))
                .andExpect(jsonPath("$.recovery_status").value("awaiting_customer_payment"))
                .andExpect(jsonPath("$.mode").value("test"));
    }

    private void makeNotRecovered(String eventId) {
        AuditRecord record = auditRepository.findByEventId(eventId).orElseThrow();
        record.setOutcome(Outcome.NOT_RECOVERED);
        record.setRecoveredAmount(BigDecimal.ZERO.setScale(2));
        record.setLifecycleState(LifecycleState.NOT_RECOVERED);
        auditRepository.saveAndFlush(record);
    }

    private void assertBlocked(String eventId, String label) throws Exception {
        mockMvc.perform(get("/api/transactions/{eventId}/next-action", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_action_allowed").value(false))
                .andExpect(jsonPath("$.action_type").value("NONE"))
                .andExpect(jsonPath("$.button_label").value(label));
        mockMvc.perform(post("/api/transactions/{eventId}/recovery-checkout", eventId))
                .andExpect(status().isConflict());
    }
}
