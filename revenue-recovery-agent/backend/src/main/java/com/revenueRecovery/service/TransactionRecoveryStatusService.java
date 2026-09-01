package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationRequest;
import com.revenueRecovery.controller.dto.TransactionRecoveryStatusResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionRecoveryStatusService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionRecoveryStatusService.class);
    private final AuditRecordRepository auditRepository;
    private final TransactionRecoveryPaymentLinkRepository linkRepository;
    private final RazorpayTestCheckoutAttemptRepository attemptRepository;
    private final RecoveryTransactionEligibilityService eligibilityService;
    private final RazorpaySignatureVerificationService verificationService;

    public TransactionRecoveryStatusService(AuditRecordRepository auditRepository,
            TransactionRecoveryPaymentLinkRepository linkRepository,
            RazorpayTestCheckoutAttemptRepository attemptRepository,
            RecoveryTransactionEligibilityService eligibilityService,
            RazorpaySignatureVerificationService verificationService) {
        this.auditRepository = auditRepository;
        this.linkRepository = linkRepository;
        this.attemptRepository = attemptRepository;
        this.eligibilityService = eligibilityService;
        this.verificationService = verificationService;
    }

    @Transactional
    public TransactionRecoveryStatusResponse check(String eventId) {
        AuditRecord record = auditRepository.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        TransactionRecoveryPaymentLink link = linkRepository
                .findForUpdateByEventEventId(eventId).orElse(null);

        if (record.getOutcome() == Outcome.RECOVERED) {
            return response(eventId, "recovered", "This transaction is already recovered.",
                    true, link == null ? RecoveryTransactionEligibilityService.NONE : link.getStatus());
        }
        if (link == null) {
            return response(eventId, "no_payment_recorded",
                    "No recovery payment has been recorded. Start a recovery Checkout first.",
                    false, RecoveryTransactionEligibilityService.NONE);
        }
        RazorpayTestCheckoutAttempt attempt = attemptRepository
                .findFirstByInternalRequestIdAndRazorpayOrderIdOrderByIdDesc(
                        link.getInternalRequestId(), link.getRazorpayOrderId()).orElse(null);
        if (attempt == null || !RazorpayCheckoutEventService.SUCCESS.equals(attempt.getEventType())
                || attempt.getRazorpayPaymentId() == null || attempt.getRazorpaySignature() == null) {
            RecoveryTransactionEligibilityService.RecoveryCheckoutDecision decision =
                    eligibilityService.evaluate(record);
            if (RecoveryTransactionEligibilityService.ABANDONED.equals(decision.existingLinkStatus())) {
                return response(eventId, "no_payment_recorded",
                        "No verified payment was recorded for the stale order. Retry Payment to create a fresh order.",
                        false, RecoveryTransactionEligibilityService.ABANDONED);
            }
            return response(eventId, "no_payment_recorded",
                    "No completed payment was reported for this order. Resume Checkout or retry after it expires.",
                    false, link.getStatus());
        }

        LOGGER.info("Manually reconciling Test Mode recovery for eventId={}, orderId={}",
                eventId, link.getRazorpayOrderId());
        RazorpaySignatureVerificationService.VerificationOutcome verification = verificationService.verify(
                new RazorpayPaymentVerificationRequest(link.getInternalRequestId(), link.getRazorpayOrderId(),
                        attempt.getRazorpayPaymentId(), attempt.getRazorpaySignature()));
        boolean recovered = verification.httpStatus().is2xxSuccessful()
                && eventId.equals(verification.response().recoveryEventId())
                && RecoveryPaymentFinalizationService.RECOVERED.equals(verification.response().recoveryStatus());
        return recovered
                ? response(eventId, "recovered", "Test Mode payment verified; transaction marked recovered.",
                        true, RecoveryPaymentFinalizationService.LINK_RECOVERED)
                : response(eventId, "verification_failed",
                        "The recorded payment could not be verified. The transaction was not changed.",
                        false, RazorpaySignatureVerificationService.FAILED);
    }

    private TransactionRecoveryStatusResponse response(String eventId, String status,
            String message, boolean recovered, String linkStatus) {
        return new TransactionRecoveryStatusResponse(eventId, status, message, recovered, linkStatus, "test");
    }
}
