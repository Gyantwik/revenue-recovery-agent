package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.controller.dto.RecoveryDemoCaseResponse;
import com.revenueRecovery.controller.dto.RecoveryLinkedOrderResponse;
import com.revenueRecovery.controller.dto.RecoveryPaymentStatusResponse;
import com.revenueRecovery.model.AuditHistory;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.RecoveryPaymentLink;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RecoveryPaymentLinkService {
    public static final String ORDER_CREATED = "order_created";
    public static final String PURPOSE = "RECOVERY_DEMO";

    private final RecoveryDemoCaseRepository caseRepository;
    private final RecoveryPaymentLinkRepository linkRepository;
    private final AuditHistoryRepository historyRepository;
    private final RecoveryPaymentEligibilityService eligibilityService;
    private final RazorpayOrderService orderService;

    public RecoveryPaymentLinkService(RecoveryDemoCaseRepository caseRepository,
            RecoveryPaymentLinkRepository linkRepository,
            AuditHistoryRepository historyRepository,
            RecoveryPaymentEligibilityService eligibilityService,
            RazorpayOrderService orderService) {
        this.caseRepository = caseRepository;
        this.linkRepository = linkRepository;
        this.historyRepository = historyRepository;
        this.eligibilityService = eligibilityService;
        this.orderService = orderService;
    }

    @Transactional(readOnly = true)
    public RecoveryDemoCaseResponse getDemoCase() {
        return toDemoResponse(caseRepository.findByEventId(RecoveryDemoCaseSeeder.DEMO_EVENT_ID)
                .orElseThrow(RecoveryPaymentException::notFound));
    }

    @Transactional
    public RecoveryLinkedOrderResponse createLinkedOrder(String eventId) {
        RecoveryDemoCase recoveryCase = caseRepository.findForUpdateByEventId(eventId)
                .orElseThrow(RecoveryPaymentException::notFound);
        eligibilityService.requireEligibleForNewLink(recoveryCase);

        RazorpayTestOrderResponse order = orderService.createRecoveryTestOrder(
                recoveryCase.getAmount(), recoveryCase.getCurrency(), recoveryCase.getEventId());
        RecoveryPaymentLink link = new RecoveryPaymentLink();
        link.setRecoveryCase(recoveryCase);
        link.setInternalRequestId(order.internalRequestId());
        link.setRazorpayOrderId(order.razorpayOrderId());
        link.setAmountInr(recoveryCase.getAmount().setScale(2));
        link.setAmountPaise(order.amount());
        link.setCurrency(recoveryCase.getCurrency());
        link.setMode("test");
        link.setPurpose(PURPOSE);
        link.setStatus(ORDER_CREATED);
        link.setCreatedAt(Instant.now());
        try {
            linkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException exception) {
            throw RecoveryPaymentException.linkExists();
        }
        return new RecoveryLinkedOrderResponse(recoveryCase.getEventId(), order.internalRequestId(),
                order.razorpayOrderId(), order.amount(), order.currency(), order.receipt(),
                ORDER_CREATED, recoveryCase.getRecoveryStatus(), "test");
    }

    @Transactional(readOnly = true)
    public RecoveryPaymentStatusResponse getStatus(String eventId) {
        RecoveryDemoCase recoveryCase = caseRepository.findByEventId(eventId)
                .orElseThrow(RecoveryPaymentException::notFound);
        RecoveryPaymentLink link = linkRepository.findByRecoveryCaseEventId(eventId).orElse(null);
        List<RecoveryPaymentStatusResponse.RecoveryAuditEntryResponse> history = historyRepository
                .findByEventIdOrderByIdAsc(eventId).stream().map(this::toAuditResponse).toList();
        return new RecoveryPaymentStatusResponse(
                recoveryCase.getEventId(), eligibilityService.isEligibleWithoutLinkCheck(recoveryCase),
                recoveryCase.getRecoveryStatus(), link == null ? null : link.getStatus(),
                link == null ? null : link.getInternalRequestId(),
                link == null ? null : link.getRazorpayOrderId(),
                link == null ? null : link.getRazorpayPaymentId(),
                link == null ? null : link.getVerifiedAt(), recoveryCase.getRecoveredAt(),
                "test", history);
    }

    private RecoveryDemoCaseResponse toDemoResponse(RecoveryDemoCase recoveryCase) {
        return new RecoveryDemoCaseResponse(recoveryCase.getEventId(), "Payment Degradation",
                "Checkout Abandoned", recoveryCase.getAmount(), recoveryCase.getCurrency(),
                "Send Recovery Link", recoveryCase.getRecoveryStatus(), "test", recoveryCase.isDemoOnly());
    }

    private RecoveryPaymentStatusResponse.RecoveryAuditEntryResponse toAuditResponse(AuditHistory history) {
        return new RecoveryPaymentStatusResponse.RecoveryAuditEntryResponse(
                history.getTimestamp(), history.getActionTaken() == null ? null : history.getActionTaken().toJson(),
                history.getOutcomeIfTerminal() == null ? null : history.getOutcomeIfTerminal().toJson(),
                history.getReason(), history.getActor().toJson());
    }
}
