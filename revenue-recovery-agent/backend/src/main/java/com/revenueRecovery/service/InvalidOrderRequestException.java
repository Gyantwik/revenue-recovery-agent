package com.revenueRecovery.service;

public class InvalidOrderRequestException extends RuntimeException {
    public InvalidOrderRequestException(String message) {
        super(message);
    }
}
