package com.revenueRecovery.service;

import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.RecoveryPaymentLink;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class RecoveryPaymentFinalizationService {
    public static final String RECOVERED = "recovered";
    public static final String LINK_RECOVERED = "recovered_by_verified_test_payment";

    private final RecoveryPaymentLinkRepository linkRepository;
    private final RecoveryDemoCaseRepository caseRepository;
    private final AuditHistoryRepository historyRepository;
    private final RecoveryPaymentEligibilityService eligibilityService;
    private final RecoveryDemoAuditService auditService;
    private final TransactionRecoveryPaymentLinkRepository transactionLinkRepository;
    private final AuditRecordRepository auditRecordRepository;
    private final RecoveryTransactionEligibilityService transactionEligibilityService;
    private final TransactionRecoveryAuditService transactionAuditService;

    public RecoveryPaymentFinalizationService(RecoveryPaymentLinkRepository linkRepository,
            RecoveryDemoCaseRepository caseRepository,
            AuditHistoryRepository historyRepository,
            RecoveryPaymentEligibilityService eligibilityService,
            RecoveryDemoAuditService auditService,
            TransactionRecoveryPaymentLinkRepository transactionLinkRepository,
            AuditRecordRepository auditRecordRepository,
            RecoveryTransactionEligibilityService transactionEligibilityService,
            TransactionRecoveryAuditService transactionAuditService) {
        this.linkRepository = linkRepository;
        this.caseRepository = caseRepository;
        this.historyRepository = historyRepository;
        this.eligibilityService = eligibilityService;
        this.auditService = auditService;
        this.transactionLinkRepository = transactionLinkRepository;
        this.auditRecordRepository = auditRecordRepository;
        this.transactionEligibilityService = transactionEligibilityService;
        this.transactionAuditService = transactionAuditService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RecoveryFinalizationResult> finalizeIfLinked(RazorpayTestOrder order,
            String paymentId, Instant verifiedAt) {
        Optional<RecoveryPaymentLink> optional = linkRepository
                .findForUpdateByInternalRequestId(order.getInternalRequestId());
        if (optional.isEmpty()) return finalizeTransactionIfLinked(order, paymentId, verifiedAt);
        RecoveryPaymentLink link = optional.get();
        RecoveryDemoCase recoveryCase = caseRepository
                .findForUpdateByEventId(link.getRecoveryCase().getEventId())
                .orElseThrow(RecoveryPaymentException::notFound);
        validateMapping(order, link, recoveryCase);
        eligibilityService.requireEligibleForFinalization(recoveryCase);

        String auditKey = recoveryCase.getEventId() + ":razorpay-test-recovery:"
                + order.getRazorpayOrderId() + ":" + paymentId;
        if (historyRepository.existsByIdempotencyKey(auditKey)) {
            throw RecoveryPaymentException.alreadyRecovered();
        }

        link.setRazorpayPaymentId(paymentId);
        link.setVerifiedAt(verifiedAt);
        link.setRecoveredAt(verifiedAt);
        link.setStatus(LINK_RECOVERED);
        recoveryCase.setOutcome(Outcome.RECOVERED);
        recoveryCase.setRecoveryStatus(RECOVERED);
        recoveryCase.setRecoveredAt(verifiedAt);
        caseRepository.save(recoveryCase);
        linkRepository.save(link);
        auditService.appendVerifiedRecovery(recoveryCase, order.getRazorpayOrderId(), paymentId, verifiedAt);
        return Optional.of(new RecoveryFinalizationResult(
                recoveryCase.getEventId(), RECOVERED, LINK_RECOVERED));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RecoveryFinalizationResult> markVerificationFailedIfLinked(RazorpayTestOrder order) {
        Optional<RecoveryFinalizationResult> demoResult = linkRepository
                .findForUpdateByInternalRequestId(order.getInternalRequestId()).map(link -> {
            if (!order.getRazorpayOrderId().equals(link.getRazorpayOrderId())) {
                throw RecoveryPaymentException.inconsistentLink();
            }
            link.setStatus(RazorpaySignatureVerificationService.FAILED);
            linkRepository.save(link);
            RecoveryDemoCase recoveryCase = link.getRecoveryCase();
            return new RecoveryFinalizationResult(recoveryCase.getEventId(),
                    recoveryCase.getRecoveryStatus(), RazorpaySignatureVerificationService.FAILED);
        });
        if (demoResult.isPresent()) return demoResult;
        return transactionLinkRepository.findForUpdateByInternalRequestId(order.getInternalRequestId()).map(link -> {
            if (!order.getRazorpayOrderId().equals(link.getRazorpayOrderId())) {
                throw RecoveryPaymentException.inconsistentLink();
            }
            link.setStatus(RazorpaySignatureVerificationService.FAILED);
            transactionLinkRepository.save(link);
            return new RecoveryFinalizationResult(link.getEvent().getEventId(),
                    TransactionRecoveryCheckoutService.AWAITING_CUSTOMER_PAYMENT,
                    RazorpaySignatureVerificationService.FAILED);
        });
    }

    private Optional<RecoveryFinalizationResult> finalizeTransactionIfLinked(RazorpayTestOrder order,
            String paymentId, Instant verifiedAt) {
        Optional<TransactionRecoveryPaymentLink> optional = transactionLinkRepository
                .findForUpdateByInternalRequestId(order.getInternalRequestId());
        if (optional.isEmpty()) return Optional.empty();
        TransactionRecoveryPaymentLink link = optional.get();
        AuditRecord record = auditRecordRepository.findForUpdateByEventId(link.getEvent().getEventId())
                .orElseThrow(RecoveryPaymentException::notFound);
        validateTransactionMapping(order, link, record);
        transactionEligibilityService.requireEligibleForFinalization(record);

        String auditKey = transactionAuditService.idempotencyKey(
                record.getEventId(), order.getRazorpayOrderId(), paymentId);
        if (historyRepository.existsByIdempotencyKey(auditKey)) {
            throw RecoveryPaymentException.alreadyRecovered();
        }

        link.setRazorpayPaymentId(paymentId);
        link.setVerifiedAt(verifiedAt);
        link.setRecoveredAt(verifiedAt);
        link.setStatus(LINK_RECOVERED);
        LifecycleState previousState = record.getLifecycleState();
        record.setOutcome(Outcome.RECOVERED);
        record.setRecoveredAmount(record.getAmount());
        record.setLifecycleState(LifecycleState.RECOVERED);
        record.setStopOrEscalateReason(null);
        transactionLinkRepository.save(link);
        transactionAuditService.appendVerifiedRecovery(record, link, previousState, paymentId, verifiedAt);
        auditRecordRepository.save(record);
        return Optional.of(new RecoveryFinalizationResult(record.getEventId(), RECOVERED, LINK_RECOVERED));
    }

    private void validateTransactionMapping(RazorpayTestOrder order,
            TransactionRecoveryPaymentLink link, AuditRecord record) {
        if (!order.getRazorpayOrderId().equals(link.getRazorpayOrderId())
                || !"test".equals(order.getMode()) || !"test".equals(link.getMode())
                || !TransactionRecoveryCheckoutService.PURPOSE.equals(link.getPurpose())
                || !RazorpayCheckoutEventService.UNVERIFIED.equals(link.getStatus())
                || order.getAmountPaise() == null || !order.getAmountPaise().equals(link.getAmountPaise())
                || record.getAmount() == null || record.getAmount().compareTo(link.getAmountInr()) != 0
                || !record.getCurrency().equals(link.getCurrency())) {
            throw RecoveryPaymentException.inconsistentLink();
        }
    }

    private void validateMapping(RazorpayTestOrder order, RecoveryPaymentLink link,
            RecoveryDemoCase recoveryCase) {
        if (!order.getRazorpayOrderId().equals(link.getRazorpayOrderId())
                || !"test".equals(order.getMode()) || !"test".equals(link.getMode())
                || !RecoveryPaymentLinkService.PURPOSE.equals(link.getPurpose())
                || !RazorpayCheckoutEventService.UNVERIFIED.equals(link.getStatus())
                || order.getAmountPaise() == null || !order.getAmountPaise().equals(link.getAmountPaise())
                || recoveryCase.getAmount().compareTo(link.getAmountInr()) != 0
                || !recoveryCase.getCurrency().equals(link.getCurrency())) {
            throw RecoveryPaymentException.inconsistentLink();
        }
    }

    public record RecoveryFinalizationResult(
            String eventId, String recoveryStatus, String linkStatus) {
    }
}
