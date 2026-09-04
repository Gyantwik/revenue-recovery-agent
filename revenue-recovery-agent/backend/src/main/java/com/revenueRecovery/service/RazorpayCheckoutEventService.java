package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.controller.dto.RazorpayCheckoutEventResponse;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class RazorpayCheckoutEventService {
    public static final String SUCCESS = "checkout_success";
    public static final String DISMISSED = "checkout_failed_or_dismissed";
    public static final String UNVERIFIED = "client_reported_unverified";
    private static final Set<String> ALLOWED_EVENTS = Set.of(SUCCESS, DISMISSED);

    private final RazorpayTestOrderRepository orderRepository;
    private final RazorpayTestCheckoutAttemptRepository attemptRepository;
    private final RecoveryPaymentLinkRepository recoveryLinkRepository;
    private final TransactionRecoveryPaymentLinkRepository transactionLinkRepository;
    private final LiveTransactionService liveTransactionService;
    private final LiveRecoveryFailureService liveRecoveryFailureService;

    public RazorpayCheckoutEventService(RazorpayTestOrderRepository orderRepository,
            RazorpayTestCheckoutAttemptRepository attemptRepository,
            RecoveryPaymentLinkRepository recoveryLinkRepository,
            TransactionRecoveryPaymentLinkRepository transactionLinkRepository,
            LiveTransactionService liveTransactionService,
            LiveRecoveryFailureService liveRecoveryFailureService) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.recoveryLinkRepository = recoveryLinkRepository;
        this.transactionLinkRepository = transactionLinkRepository;
        this.liveTransactionService = liveTransactionService;
        this.liveRecoveryFailureService = liveRecoveryFailureService;
    }

    @Transactional
    public RazorpayCheckoutEventResponse record(RazorpayCheckoutEventRequest request) {
        validateBaseRequest(request);
        RazorpayTestOrder order = orderRepository.findByInternalRequestId(request.internalRequestId())
                .orElseThrow(CheckoutEventException::orderNotFound);
        if (!order.getRazorpayOrderId().equals(request.razorpayOrderId())) {
            throw CheckoutEventException.orderMismatch();
        }

        boolean success = SUCCESS.equals(request.eventType());
        if (success && (isBlank(request.razorpayPaymentId()) || isBlank(request.razorpaySignature()))) {
            throw CheckoutEventException.invalid("Payment ID and signature are required for checkout_success.");
        }
        if (!success && (!isBlank(request.razorpayPaymentId()) || !isBlank(request.razorpaySignature()))) {
            throw CheckoutEventException.invalid("Payment ID and signature are allowed only for checkout_success.");
        }
        if (success && attemptRepository.existsByRazorpayOrderIdAndRazorpayPaymentId(
                request.razorpayOrderId(), request.razorpayPaymentId())) {
            throw CheckoutEventException.duplicate();
        }

        Instant now = Instant.now();
        RazorpayTestCheckoutAttempt attempt = new RazorpayTestCheckoutAttempt();
        attempt.setInternalRequestId(request.internalRequestId());
        attempt.setRazorpayOrderId(request.razorpayOrderId());
        attempt.setRazorpayPaymentId(blankToNull(request.razorpayPaymentId()));
        attempt.setRazorpaySignature(blankToNull(request.razorpaySignature()));
        attempt.setEventType(request.eventType());
        attempt.setReason(trimmedOrNull(request.reason(), 100));
        attempt.setErrorCode(trimmedOrNull(request.errorCode(), 100));
        attempt.setErrorDescription(trimmedOrNull(request.errorDescription(), 500));
        attempt.setErrorSource(trimmedOrNull(request.errorSource(), 100));
        attempt.setErrorStep(trimmedOrNull(request.errorStep(), 100));
        attempt.setCustomerRef(trimmedOrNull(request.customerRef(), 120));
        attempt.setLatencyMs(request.latencyMs());
        attempt.setStatus(UNVERIFIED);
        attempt.setCreatedAt(now);
        try {
            attemptRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException exception) {
            if (success) throw CheckoutEventException.duplicate();
            throw exception;
        }

        order.setStatus(UNVERIFIED);
        orderRepository.save(order);
        recoveryLinkRepository.findByInternalRequestId(request.internalRequestId()).ifPresent(link -> {
            link.setStatus(UNVERIFIED);
            recoveryLinkRepository.save(link);
        });
        transactionLinkRepository.findByInternalRequestId(request.internalRequestId()).ifPresent(link -> {
            link.setStatus(UNVERIFIED);
            transactionLinkRepository.save(link);
        });

        boolean linkedRecovery = recoveryLinkRepository.findByInternalRequestId(request.internalRequestId()).isPresent()
                || transactionLinkRepository.findByInternalRequestId(request.internalRequestId()).isPresent();

        // Standalone Checkout intake is represented on the live dashboard for both outcomes.
        // A client-reported success remains pending until server-side verification; it is not
        // marked recovered and it does not create synthetic audit history.
        if (!linkedRecovery) {
            liveTransactionService.record(order, request);
        }

        if (linkedRecovery && !success) {
            liveRecoveryFailureService.recordIfLinked(request);
        }

        return new RazorpayCheckoutEventResponse(request.internalRequestId(), request.razorpayOrderId(),
                blankToNull(request.razorpayPaymentId()), request.eventType(), UNVERIFIED, now);
    }

    private void validateBaseRequest(RazorpayCheckoutEventRequest request) {
        if (request == null) throw CheckoutEventException.invalid("Request body is required.");
        if (isBlank(request.internalRequestId()) || isBlank(request.razorpayOrderId())) {
            throw CheckoutEventException.invalid("Internal request ID and Razorpay order ID are required.");
        }
        if (!ALLOWED_EVENTS.contains(request.eventType())) {
            throw CheckoutEventException.invalid("Unknown checkout event type.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private String trimmedOrNull(String value, int maxLength) {
        if (isBlank(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
