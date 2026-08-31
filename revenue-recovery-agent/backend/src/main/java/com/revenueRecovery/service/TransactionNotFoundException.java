package com.revenueRecovery.service;

public class TransactionNotFoundException extends RuntimeException {
    private final String eventId;
    public TransactionNotFoundException(String eventId) {
        super("Transaction not found");
        this.eventId = eventId;
    }
    public String getEventId() { return eventId; }
}
