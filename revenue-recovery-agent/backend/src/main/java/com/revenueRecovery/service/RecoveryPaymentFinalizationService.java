package com.revenueRecovery.service;

import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.RecoveryPaymentLink;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
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

    public RecoveryPaymentFinalizationService(RecoveryPaymentLinkRepository linkRepository,
            RecoveryDemoCaseRepository caseRepository,
            AuditHistoryRepository historyRepository,
            RecoveryPaymentEligibilityService eligibilityService,
            RecoveryDemoAuditService auditService) {
        this.linkRepository = linkRepository;
        this.caseRepository = caseRepository;
        this.historyRepository = historyRepository;
        this.eligibilityService = eligibilityService;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RecoveryFinalizationResult> finalizeIfLinked(RazorpayTestOrder order,
            String paymentId, Instant verifiedAt) {
        Optional<RecoveryPaymentLink> optional = linkRepository
                .findForUpdateByInternalRequestId(order.getInternalRequestId());
        if (optional.isEmpty()) return Optional.empty();
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
        return linkRepository.findForUpdateByInternalRequestId(order.getInternalRequestId()).map(link -> {
            if (!order.getRazorpayOrderId().equals(link.getRazorpayOrderId())) {
                throw RecoveryPaymentException.inconsistentLink();
            }
            link.setStatus(RazorpaySignatureVerificationService.FAILED);
            linkRepository.save(link);
            RecoveryDemoCase recoveryCase = link.getRecoveryCase();
            return new RecoveryFinalizationResult(recoveryCase.getEventId(),
                    recoveryCase.getRecoveryStatus(), RazorpaySignatureVerificationService.FAILED);
        });
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
