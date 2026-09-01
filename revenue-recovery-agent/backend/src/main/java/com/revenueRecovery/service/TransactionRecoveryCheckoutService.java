package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.controller.dto.TransactionRecoveryOrderResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransactionRecoveryCheckoutService {
    public static final String PURPOSE = "TRANSACTION_RECOVERY";
    public static final String AWAITING_CUSTOMER_PAYMENT = "awaiting_customer_payment";
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionRecoveryCheckoutService.class);

    private final AuditRecordRepository auditRepository;
    private final EventRepository eventRepository;
    private final TransactionRecoveryPaymentLinkRepository linkRepository;
    private final RazorpayTestOrderRepository razorpayOrderRepository;
    private final RecoveryTransactionEligibilityService eligibilityService;
    private final RazorpayOrderService orderService;

    public TransactionRecoveryCheckoutService(AuditRecordRepository auditRepository,
            EventRepository eventRepository,
            TransactionRecoveryPaymentLinkRepository linkRepository,
            RazorpayTestOrderRepository razorpayOrderRepository,
            RecoveryTransactionEligibilityService eligibilityService,
            RazorpayOrderService orderService) {
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
        this.linkRepository = linkRepository;
        this.razorpayOrderRepository = razorpayOrderRepository;
        this.eligibilityService = eligibilityService;
        this.orderService = orderService;
    }

    @Transactional
    public TransactionRecoveryOrderResponse createOrResume(String eventId) {
        AuditRecord audit = auditRepository.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        Event event = eventRepository.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        RecoveryTransactionEligibilityService.RecoveryCheckoutDecision decision =
                eligibilityService.requireEligibleForNewOrder(audit);
        validatePersistedMoney(audit, event);

        TransactionRecoveryPaymentLink existing = linkRepository
                .findForUpdateByEventEventId(eventId).orElse(null);
        if (decision.actionType() == NextActionType.RESUME_RECOVERY_CHECKOUT) {
            if (existing == null || !eligibilityService.isActive(existing, Instant.now())) {
                throw RecoveryPaymentException.notAllowed("The recovery order is no longer resumable; retry.");
            }
            RazorpayTestOrder order = razorpayOrderRepository
                    .findByInternalRequestId(existing.getInternalRequestId())
                    .orElseThrow(RecoveryPaymentException::inconsistentLink);
            LOGGER.info("Resuming Test Mode recovery order for eventId={}, orderId={}",
                    eventId, order.getRazorpayOrderId());
            return response(eventId, order, existing);
        }

        RazorpayTestOrderResponse order = orderService.createRecoveryTestOrder(
                audit.getAmount(), audit.getCurrency(), eventId);
        Instant now = Instant.now();
        TransactionRecoveryPaymentLink link = existing == null
                ? new TransactionRecoveryPaymentLink() : existing;
        link.setEvent(event);
        link.setInternalRequestId(order.internalRequestId());
        link.setRazorpayOrderId(order.razorpayOrderId());
        link.setRazorpayPaymentId(null);
        link.setAmountInr(audit.getAmount().setScale(2));
        link.setAmountPaise(order.amount());
        link.setCurrency(audit.getCurrency());
        link.setMode("test");
        link.setPurpose(PURPOSE);
        link.setRecoveryAction(decision.recoveryAction());
        link.setStatus(RecoveryPaymentLinkService.ORDER_CREATED);
        link.setCreatedAt(now);
        link.setVerifiedAt(null);
        link.setRecoveredAt(null);
        try {
            linkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException exception) {
            throw RecoveryPaymentException.linkExists();
        }
        LOGGER.info("Created Test Mode recovery order for eventId={}, orderId={}, amountPaise={}",
                eventId, order.razorpayOrderId(), order.amount());
        return new TransactionRecoveryOrderResponse(eventId, order.internalRequestId(),
                order.razorpayOrderId(), order.amount(), order.currency(), order.receipt(),
                decision.recoveryAction(), RecoveryPaymentLinkService.ORDER_CREATED,
                AWAITING_CUSTOMER_PAYMENT, "test");
    }

    private TransactionRecoveryOrderResponse response(String eventId, RazorpayTestOrder order,
            TransactionRecoveryPaymentLink link) {
        return new TransactionRecoveryOrderResponse(eventId, order.getInternalRequestId(),
                order.getRazorpayOrderId(), order.getAmountPaise(), order.getCurrency(), order.getReceipt(),
                link.getRecoveryAction(), link.getStatus(), AWAITING_CUSTOMER_PAYMENT, "test");
    }

    private void validatePersistedMoney(AuditRecord audit, Event event) {
        if (audit.getAmount() == null || event.getAmount() == null
                || audit.getAmount().compareTo(event.getAmount()) != 0
                || !"INR".equals(audit.getCurrency()) || !audit.getCurrency().equals(event.getCurrency())) {
            throw RecoveryPaymentException.notAllowed("Persisted transaction amount or currency is inconsistent.");
        }
    }
}
