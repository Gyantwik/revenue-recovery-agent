package com.revenueRecovery.service;

import org.springframework.http.HttpStatus;

public class RecoveryPaymentException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    private RecoveryPaymentException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static RecoveryPaymentException notFound() {
        return new RecoveryPaymentException(HttpStatus.NOT_FOUND,
                "Recovery case not found", "The requested recovery case does not exist.");
    }

    public static RecoveryPaymentException notAllowed() {
        return new RecoveryPaymentException(HttpStatus.CONFLICT,
                "Recovery payment not allowed", "Recovery payment is not allowed for this policy outcome.");
    }

    public static RecoveryPaymentException alreadyRecovered() {
        return new RecoveryPaymentException(HttpStatus.CONFLICT,
                "Recovery case already completed", "This case is already recovered.");
    }

    public static RecoveryPaymentException linkExists() {
        return new RecoveryPaymentException(HttpStatus.CONFLICT,
                "Recovery payment link exists", "A recovery payment link already exists for this case.");
    }

    public static RecoveryPaymentException inconsistentLink() {
        return new RecoveryPaymentException(HttpStatus.CONFLICT,
                "Recovery payment mapping invalid", "The verified order does not match its recovery case link.");
    }

    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
}
