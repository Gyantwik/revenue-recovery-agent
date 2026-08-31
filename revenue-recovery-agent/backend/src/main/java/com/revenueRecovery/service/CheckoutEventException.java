package com.revenueRecovery.service;

import org.springframework.http.HttpStatus;

public class CheckoutEventException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    private CheckoutEventException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static CheckoutEventException invalid(String message) {
        return new CheckoutEventException(HttpStatus.BAD_REQUEST, "Invalid checkout event", message);
    }

    public static CheckoutEventException orderNotFound() {
        return new CheckoutEventException(HttpStatus.NOT_FOUND, "Test order not found",
                "The internal request does not identify a test order.");
    }

    public static CheckoutEventException orderMismatch() {
        return new CheckoutEventException(HttpStatus.BAD_REQUEST, "Invalid checkout event",
                "Razorpay order ID does not belong to the internal request.");
    }

    public static CheckoutEventException duplicate() {
        return new CheckoutEventException(HttpStatus.CONFLICT, "Duplicate checkout event",
                "This client-reported success was already recorded.");
    }

    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
}
