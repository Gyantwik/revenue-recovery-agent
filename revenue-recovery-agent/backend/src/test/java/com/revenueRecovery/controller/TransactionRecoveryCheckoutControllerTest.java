package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired TransactionRecoveryPaymentLinkRepository transactionLinkRepository;
    @MockBean RazorpayOrderService orderService;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void seedAndMockOrderCreation() throws Exception {
        batchService.runBatch();
        when(orderService.createRecoveryTestOrder(any(BigDecimal.class), eq("INR"), any(String.class)))
                .thenAnswer(invocation -> {
                    BigDecimal amount = invocation.getArgument(0);
                    int id = sequence.incrementAndGet();
                    RazorpayTestOrder order = new RazorpayTestOrder();
                    order.setInternalRequestId("req_recovery_" + id);
                    order.setRazorpayOrderId("order_recovery_" + id);
                    order.setAmountInr(amount.setScale(2));
                    order.setAmountPaise(RazorpayAmountConverter.toPaise(amount));
                    order.setCurrency("INR");
                    order.setReceipt("recoverai_recovery_" + id);
                    order.setStatus("created");
                    order.setMode("test");
                    order.setCreatedAt(Instant.now());
                    orderRepository.saveAndFlush(order);
                    return new RazorpayTestOrderResponse(order.getInternalRequestId(), order.getRazorpayOrderId(),
                            order.getAmountPaise(), order.getCurrency(), order.getReceipt(),
                            order.getStatus(), order.getMode());
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
        assertCreated("TXN10043", 315000, "RESUME_PAYMENT");
        assertBlocked("TXN10036", "Choose Another Payment Method");
        assertBlocked("TXN10006", "No Recovery Payment");
        assertBlocked("TXN10014", "Retry Limit Reached");

        AuditRecord mandate = auditRepository.findByEventId("TXN10056").orElseThrow();
        mandate.setOutcome(Outcome.NOT_RECOVERED);
        mandate.setLifecycleState(LifecycleState.RETRY_EXHAUSTED);
        auditRepository.saveAndFlush(mandate);
        assertCreated("TXN10056", 59900, "PAY_MANUALLY");
    }

    @Test
    void blockedCategoriesReturnPolicyGuidanceAndNeverCreateOrder() throws Exception {
        makeNotRecovered("TXN10032");
        mockMvc.perform(get("/api/transactions/TXN10032/next-action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_action_allowed").value(true))
                .andExpect(jsonPath("$.action_type").value("DISPLAY_INFORMATION"))
                .andExpect(jsonPath("$.button_label").value("Verify Status"));
        assertBlocked("TXN10022", "Verify Previous Payment First");
        assertBlocked("TXN10001", "No Automatic Recovery Allowed");
        assertBlocked("TXN10048", "Escalated for Review");
        assertBlocked("TXN10010", "Escalated for Review");
        assertBlocked("TXN10051", "Escalated for Review");
    }

    @Test
    void scheduledRetryIsBlockedAndActiveLinkResumesTheSameOrder() throws Exception {
        AuditRecord bank = auditRepository.findByEventId("TXN10014").orElseThrow();
        bank.setOutcome(Outcome.NOT_RECOVERED);
        bank.setRecoveredAmount(BigDecimal.ZERO.setScale(2));
        bank.setAttemptNumber(1);
        bank.setLifecycleState(LifecycleState.RETRY_SCHEDULED);
        auditRepository.saveAndFlush(bank);
        assertBlocked("TXN10014", "Retry Scheduled");

        makeNotRecovered("TXN10044");
        String firstOrder = mockMvc.perform(post("/api/transactions/TXN10044/recovery-checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link_status").value("order_created"))
                .andReturn().getResponse().getContentAsString();
        String secondOrder = mockMvc.perform(post("/api/transactions/TXN10044/recovery-checkout"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(firstOrder, secondOrder);
        mockMvc.perform(get("/api/transactions/TXN10044/next-action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_action_allowed").value(true))
                .andExpect(jsonPath("$.action_type").value("RESUME_RECOVERY_CHECKOUT"))
                .andExpect(jsonPath("$.secondary_action_type").value("CHECK_PAYMENT_STATUS"));
        verify(orderService, times(1)).createRecoveryTestOrder(any(BigDecimal.class), eq("INR"), eq("TXN10044"));
        AuditRecord unchanged = auditRepository.findByEventId("TXN10044").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Outcome.NOT_RECOVERED, unchanged.getOutcome());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                unchanged.getRecoveredAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void staleLinkIsAbandonedAndFreshOrderCanBeCreated() throws Exception {
        makeNotRecovered("TXN10044");
        mockMvc.perform(post("/api/transactions/TXN10044/recovery-checkout"))
                .andExpect(status().isOk());
        TransactionRecoveryPaymentLink link = transactionLinkRepository
                .findByEventEventId("TXN10044").orElseThrow();
        String oldOrderId = link.getRazorpayOrderId();
        link.setCreatedAt(Instant.now().minus(31, ChronoUnit.MINUTES));
        transactionLinkRepository.saveAndFlush(link);

        mockMvc.perform(get("/api/transactions/TXN10044/next-action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.button_label").value("Retry Payment"))
                .andExpect(jsonPath("$.existing_link_status").value("abandoned"));
        mockMvc.perform(post("/api/transactions/TXN10044/recovery-checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpay_order_id").value(org.hamcrest.Matchers.not(oldOrderId)))
                .andExpect(jsonPath("$.link_status").value("order_created"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                transactionLinkRepository.count());
    }

    private void assertCreated(String eventId, long amountPaise, String action) throws Exception {
        mockMvc.perform(post("/api/transactions/{eventId}/recovery-checkout", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value(eventId))
                .andExpect(jsonPath("$.amount").value(amountPaise))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.recovery_action").value(action))
                .andExpect(jsonPath("$.link_status").value("order_created"))
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
