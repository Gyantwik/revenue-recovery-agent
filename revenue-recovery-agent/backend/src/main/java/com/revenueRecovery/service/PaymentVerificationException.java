package com.revenueRecovery.service;

import org.springframework.http.HttpStatus;

public class PaymentVerificationException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    private PaymentVerificationException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static PaymentVerificationException invalid(String message) {
        return new PaymentVerificationException(HttpStatus.BAD_REQUEST,
                "Invalid verification request", message);
    }

    public static PaymentVerificationException orderNotFound() {
        return new PaymentVerificationException(HttpStatus.NOT_FOUND,
                "Test order not found", "The internal request does not identify a test order.");
    }

    public static PaymentVerificationException orderMismatch() {
        return new PaymentVerificationException(HttpStatus.BAD_REQUEST,
                "Order mismatch", "Razorpay order ID does not belong to the internal request.");
    }

    public static PaymentVerificationException missingSuccessAttempt() {
        return new PaymentVerificationException(HttpStatus.CONFLICT,
                "Checkout success not recorded",
                "A matching client-reported checkout success is required before verification.");
    }

    public static PaymentVerificationException terminal(String message) {
        return new PaymentVerificationException(HttpStatus.CONFLICT,
                "Verification already finalized", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
}
