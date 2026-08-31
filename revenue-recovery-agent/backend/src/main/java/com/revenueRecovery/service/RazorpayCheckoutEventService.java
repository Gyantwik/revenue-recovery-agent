package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.controller.dto.RazorpayCheckoutEventResponse;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
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

    public RazorpayCheckoutEventService(RazorpayTestOrderRepository orderRepository,
            RazorpayTestCheckoutAttemptRepository attemptRepository) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
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
