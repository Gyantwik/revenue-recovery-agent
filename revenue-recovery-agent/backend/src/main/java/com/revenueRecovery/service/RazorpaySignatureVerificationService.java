package com.revenueRecovery.service;

import com.revenueRecovery.config.RazorpayProperties;
import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationRequest;
import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationResponse;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class RazorpaySignatureVerificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RazorpaySignatureVerificationService.class);
    public static final String VERIFIED = "verified_test_payment";
    public static final String FAILED = "verification_failed";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String FAILURE_CODE = "SIGNATURE_MISMATCH";

    private final RazorpayProperties properties;
    private final RazorpayTestOrderRepository orderRepository;
    private final RazorpayTestCheckoutAttemptRepository attemptRepository;
    private final RecoveryPaymentFinalizationService recoveryFinalizationService;
    private final LiveTransactionService liveTransactionService;

    @Autowired
    public RazorpaySignatureVerificationService(RazorpayProperties properties,
            RazorpayTestOrderRepository orderRepository,
            RazorpayTestCheckoutAttemptRepository attemptRepository,
            RecoveryPaymentFinalizationService recoveryFinalizationService,
            LiveTransactionService liveTransactionService) {
        this.properties = properties;
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.recoveryFinalizationService = recoveryFinalizationService;
        this.liveTransactionService = liveTransactionService;
    }

    // Retained for focused unit tests that exercise HMAC verification in isolation.
    RazorpaySignatureVerificationService(RazorpayProperties properties,
            RazorpayTestOrderRepository orderRepository,
            RazorpayTestCheckoutAttemptRepository attemptRepository,
            RecoveryPaymentFinalizationService recoveryFinalizationService) {
        this(properties, orderRepository, attemptRepository, recoveryFinalizationService, null);
    }

    @Transactional
    public VerificationOutcome verify(RazorpayPaymentVerificationRequest request) {
        validateRequest(request);
        validateConfiguration();

        RazorpayTestOrder order = orderRepository.findForUpdateByInternalRequestId(request.internalRequestId())
                .orElseThrow(PaymentVerificationException::orderNotFound);
        if (!order.getRazorpayOrderId().equals(request.razorpayOrderId())) {
            throw PaymentVerificationException.orderMismatch();
        }
        if (!"test".equals(order.getMode())) {
            throw PaymentVerificationException.invalid("Only Test Mode orders can be verified.");
        }

        rejectTerminalOrder(order, request.razorpayPaymentId());
        RazorpayTestCheckoutAttempt attempt = attemptRepository
                .findByInternalRequestIdAndRazorpayOrderIdAndRazorpayPaymentId(
                        request.internalRequestId(), order.getRazorpayOrderId(), request.razorpayPaymentId())
                .orElseThrow(PaymentVerificationException::missingSuccessAttempt);
        if (!RazorpayCheckoutEventService.SUCCESS.equals(attempt.getEventType())
                || !RazorpayCheckoutEventService.UNVERIFIED.equals(attempt.getStatus())) {
            throw PaymentVerificationException.missingSuccessAttempt();
        }

        String persistedSignature = attempt.getRazorpaySignature();
        boolean callbackMatches = persistedSignature != null
                && constantTimeEquals(persistedSignature.getBytes(StandardCharsets.UTF_8),
                        request.razorpaySignature().getBytes(StandardCharsets.UTF_8));
        boolean hmacMatches = callbackMatches && verifySignature(
                order.getRazorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature(), properties.getKeySecret());

        if (!hmacMatches) {
            LOGGER.warn("Test Mode signature verification failed for requestId={}, orderId={}, paymentId={}",
                    request.internalRequestId(), order.getRazorpayOrderId(), request.razorpayPaymentId());
            attempt.setStatus(FAILED);
            attempt.setVerificationFailureCode(FAILURE_CODE);
            attempt.setRazorpaySignature(null);
            order.setStatus(FAILED);
            attemptRepository.save(attempt);
            orderRepository.save(order);
            RecoveryPaymentFinalizationService.RecoveryFinalizationResult recoveryResult =
                    recoveryFinalizationService.markVerificationFailedIfLinked(order).orElse(null);
            return new VerificationOutcome(HttpStatus.UNPROCESSABLE_ENTITY,
                    response(request, order, FAILED, null, recoveryResult));
        }

        Instant verifiedAt = Instant.now();
        LOGGER.info("Test Mode signature verified for requestId={}, orderId={}, paymentId={}",
                request.internalRequestId(), order.getRazorpayOrderId(), request.razorpayPaymentId());
        attempt.setStatus(VERIFIED);
        attempt.setVerifiedAt(verifiedAt);
        attempt.setVerificationFailureCode(null);
        attempt.setRazorpaySignature(null);
        order.setStatus(VERIFIED);
        attemptRepository.save(attempt);
        orderRepository.save(order);
        RecoveryPaymentFinalizationService.RecoveryFinalizationResult recoveryResult =
                recoveryFinalizationService.finalizeIfLinked(
                        order, request.razorpayPaymentId(), verifiedAt).orElse(null);
        if (recoveryResult == null && liveTransactionService != null) {
            liveTransactionService.markVerified(order.getRazorpayOrderId(), request.razorpayPaymentId());
        }
        return new VerificationOutcome(HttpStatus.OK,
                response(request, order, VERIFIED, verifiedAt, recoveryResult));
    }

    boolean verifySignature(String storedOrderId, String paymentId, String receivedSignature, String secret) {
        try {
            byte[] expectedBytes = HexFormat.of().parseHex(calculateSignature(storedOrderId, paymentId, secret));
            byte[] receivedBytes = HexFormat.of().parseHex(receivedSignature);
            return constantTimeEquals(expectedBytes, receivedBytes);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    boolean constantTimeEquals(byte[] expected, byte[] received) {
        return MessageDigest.isEqual(expected, received);
    }

    String calculateSignature(String storedOrderId, String paymentId, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((storedOrderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private void validateRequest(RazorpayPaymentVerificationRequest request) {
        if (request == null || isBlank(request.internalRequestId()) || isBlank(request.razorpayOrderId())
                || isBlank(request.razorpayPaymentId()) || isBlank(request.razorpaySignature())) {
            throw PaymentVerificationException.invalid("All verification fields are required.");
        }
    }

    private void validateConfiguration() {
        String keyId = properties.getKeyId();
        String secret = properties.getKeySecret();
        if (isBlank(keyId) || isBlank(secret) || !keyId.startsWith("rzp_test_")
                || keyId.startsWith("rzp_live_")) {
            throw RazorpayServiceException.notConfigured();
        }
    }

    private void rejectTerminalOrder(RazorpayTestOrder order, String paymentId) {
        if (VERIFIED.equals(order.getStatus())) {
            RazorpayTestCheckoutAttempt verified = attemptRepository
                    .findFirstByRazorpayOrderIdAndStatus(order.getRazorpayOrderId(), VERIFIED)
                    .orElse(null);
            String message = verified != null && paymentId.equals(verified.getRazorpayPaymentId())
                    ? "This Test Mode payment is already verified."
                    : "This Test Mode order is already verified with a different payment ID.";
            throw PaymentVerificationException.terminal(message);
        }
        if (FAILED.equals(order.getStatus())) {
            throw PaymentVerificationException.terminal(
                    "Verification previously failed and this terminal attempt cannot be retried.");
        }
    }

    private RazorpayPaymentVerificationResponse response(RazorpayPaymentVerificationRequest request,
            RazorpayTestOrder order, String status, Instant verifiedAt,
            RecoveryPaymentFinalizationService.RecoveryFinalizationResult recoveryResult) {
        return new RazorpayPaymentVerificationResponse(request.internalRequestId(),
                order.getRazorpayOrderId(), request.razorpayPaymentId(), status, "test", verifiedAt,
                recoveryResult == null ? null : recoveryResult.eventId(),
                recoveryResult == null ? null : recoveryResult.recoveryStatus(),
                recoveryResult == null ? null : recoveryResult.linkStatus());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record VerificationOutcome(HttpStatus httpStatus,
            RazorpayPaymentVerificationResponse response) {
    }
}
