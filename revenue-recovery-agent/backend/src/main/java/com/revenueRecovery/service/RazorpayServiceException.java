package com.revenueRecovery.service;

import org.springframework.http.HttpStatus;

public class RazorpayServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    private RazorpayServiceException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static RazorpayServiceException notConfigured() {
        return new RazorpayServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "Razorpay unavailable", "Razorpay Test Mode is not configured.");
    }

    public static RazorpayServiceException upstreamFailure() {
        return new RazorpayServiceException(HttpStatus.BAD_GATEWAY,
                "Razorpay upstream error", "Razorpay Test Mode order creation failed.");
    }

    public static RazorpayServiceException temporarilyUnavailable() {
        return new RazorpayServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "Razorpay unavailable", "Razorpay Test Mode service is temporarily unavailable.");
    }

    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
}
