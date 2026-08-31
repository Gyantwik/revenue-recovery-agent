package com.revenueRecovery.controller;

import com.revenueRecovery.service.RecoveryActionBlockedException;
import com.revenueRecovery.service.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.LinkedHashMap;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecoveryActionBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBlockedAction(RecoveryActionBlockedException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_id", exception.getEventId());
        body.put("current_state", exception.getCurrentState());
        body.put("requested_action", exception.getRequestedAction());
        body.put("reason", exception.getMessage());
        if (exception.getNextEligibleActionAt() != null) {
            body.put("next_eligible_action_at", exception.getNextEligibleActionAt());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTransactionNotFound(TransactionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Transaction not found", "eventId", exception.getEventId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unexpected API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
