package com.revenueRecovery.service;

import com.revenueRecovery.model.ClassificationResult;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.enums.RootCause;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationServiceTest {

    private final ClassificationService classificationService = new ClassificationService();

    @Test
    void classifiesEveryAmbiguousCodeBelowTheConfidenceGate() {
        Map<String, RootCause> ambiguousCodes = new LinkedHashMap<>();
        ambiguousCodes.put("ERR_UNKNOWN_X9", RootCause.MERCHANT_GATEWAY_ISSUE);
        ambiguousCodes.put("99_SYSTEM_MALFUNCTION", RootCause.BANK_TEMP_ERROR);
        ambiguousCodes.put("STATUS_UNAVAILABLE_WAIT", RootCause.PAYMENT_PENDING);
        ambiguousCodes.put("UNKNOWN_MANDATE_ERR_88", RootCause.MANDATE_FAILED_RETRYABLE);
        ambiguousCodes.put("STATUS_UNKNOWN", RootCause.PAYMENT_PENDING);

        ambiguousCodes.forEach((code, expectedCause) -> {
            ClassificationResult result = classify("gateway_response_code: " + code);
            assertEquals(expectedCause, result.getRootCause(), code);
            assertTrue(result.getConfidence() < 0.75, code);
            assertEquals("rule_based_lookup", result.getMethod(), code);
        });
    }

    @Test
    void classifiesOneClearSignalForEveryKnownRootCause() {
        Map<String, RootCause> clearSignals = new LinkedHashMap<>();
        clearSignals.put("cancelled_by_user", RootCause.USER_CANCELLED);
        clearSignals.put("INVALID_PIN", RootCause.INCORRECT_PIN);
        clearSignals.put("gateway_response_code: BANK_TIMEOUT", RootCause.BANK_TEMP_ERROR);
        clearSignals.put("network_drop detected by client", RootCause.WEAK_NETWORK);
        clearSignals.put("PENDING_WITH_BANK", RootCause.PAYMENT_PENDING);
        clearSignals.put("51_INSUFFICIENT_FUNDS", RootCause.INSUFFICIENT_BALANCE);
        clearSignals.put("ui_event: TAB_CLOSED", RootCause.CHECKOUT_ABANDONED);
        clearSignals.put("GATEWAY_ROUTING_ERROR", RootCause.MERCHANT_GATEWAY_ISSUE);
        clearSignals.put("U30_MANDATE_REVOKED", RootCause.MANDATE_EXPIRED);
        clearSignals.put("ISSUER_NODE_OFFLINE", RootCause.MANDATE_FAILED_RETRYABLE);

        clearSignals.forEach((signal, expectedCause) -> {
            ClassificationResult result = classify(signal);
            assertEquals(expectedCause, result.getRootCause(), signal);
            assertTrue(result.getConfidence() >= 0.75, signal);
            assertEquals("rule_based_lookup", result.getMethod(), signal);
        });
    }

    @Test
    void returnsUnknownForAnUnrecognizedSignal() {
        ClassificationResult result = classify("unrecognized_gateway_code");

        assertEquals(RootCause.UNKNOWN, result.getRootCause());
        assertEquals(0.30, result.getConfidence());
        assertEquals("rule_based_lookup", result.getMethod());
    }

    private ClassificationResult classify(String signal) {
        Event event = new Event();
        event.setSignalsUsed(List.of(signal));
        return classificationService.classify(event);
    }
}
