package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.controller.dto.TransactionRecoveryOrderResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransactionRecoveryCheckoutService {
    public static final String PURPOSE = "TRANSACTION_RECOVERY";
    public static final String AWAITING_CUSTOMER_PAYMENT = "awaiting_customer_payment";

    private final AuditRecordRepository auditRepository;
    private final EventRepository eventRepository;
    private final TransactionRecoveryPaymentLinkRepository linkRepository;
    private final RecoveryTransactionEligibilityService eligibilityService;
    private final RazorpayOrderService orderService;

    public TransactionRecoveryCheckoutService(AuditRecordRepository auditRepository,
            EventRepository eventRepository,
            TransactionRecoveryPaymentLinkRepository linkRepository,
            RecoveryTransactionEligibilityService eligibilityService,
            RazorpayOrderService orderService) {
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
        this.linkRepository = linkRepository;
        this.eligibilityService = eligibilityService;
        this.orderService = orderService;
    }

    @Transactional
    public TransactionRecoveryOrderResponse create(String eventId) {
        AuditRecord audit = auditRepository.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        Event event = eventRepository.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        RecoveryTransactionEligibilityService.RecoveryCheckoutDecision decision =
                eligibilityService.requireEligibleForNewLink(audit);

        if (audit.getAmount() == null || event.getAmount() == null
                || audit.getAmount().compareTo(event.getAmount()) != 0
                || !"INR".equals(audit.getCurrency()) || !audit.getCurrency().equals(event.getCurrency())) {
            throw RecoveryPaymentException.notAllowed("Persisted transaction amount or currency is inconsistent.");
        }

        RazorpayTestOrderResponse order = orderService.createRecoveryTestOrder(
                audit.getAmount(), audit.getCurrency(), eventId);
        TransactionRecoveryPaymentLink link = new TransactionRecoveryPaymentLink();
        link.setEvent(event);
        link.setInternalRequestId(order.internalRequestId());
        link.setRazorpayOrderId(order.razorpayOrderId());
        link.setAmountInr(audit.getAmount().setScale(2));
        link.setAmountPaise(order.amount());
        link.setCurrency(audit.getCurrency());
        link.setMode("test");
        link.setPurpose(PURPOSE);
        link.setRecoveryAction(decision.recoveryAction());
        link.setStatus(RecoveryPaymentLinkService.ORDER_CREATED);
        link.setCreatedAt(Instant.now());
        try {
            linkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException exception) {
            throw RecoveryPaymentException.linkExists();
        }

        return new TransactionRecoveryOrderResponse(eventId, order.internalRequestId(),
                order.razorpayOrderId(), order.amount(), order.currency(), order.receipt(),
                decision.recoveryAction(), AWAITING_CUSTOMER_PAYMENT, "test");
    }
}
