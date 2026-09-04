package com.revenueRecovery.service;

import com.revenueRecovery.model.ClassificationResult;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.enums.RootCause;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class FailureClassifierService {
    public static final String METHOD = "real_signal_mapped";

    // Standardized mapping including Razorpay Sandbox test return codes
    private static final Map<String, RootCause> MAPPING = Map.ofEntries(
            // Direct simulator keys
            Map.entry("gateway_technical_error", RootCause.BANK_TEMP_ERROR),
            Map.entry("insufficient_fund", RootCause.INSUFFICIENT_BALANCE),
            Map.entry("insufficient_funds", RootCause.INSUFFICIENT_BALANCE),
            Map.entry("payment_cancelled", RootCause.USER_CANCELLED),
            Map.entry("user_cancelled", RootCause.USER_CANCELLED),
            Map.entry("authentication_failed", RootCause.INCORRECT_PIN),
            Map.entry("invalid_pin", RootCause.INCORRECT_PIN),
            Map.entry("payment_timed_out", RootCause.WEAK_NETWORK),
            Map.entry("network_error", RootCause.WEAK_NETWORK),

            // Razorpay Sandbox standard test failure strings
            Map.entry("payment_failed", RootCause.BANK_TEMP_ERROR),
            Map.entry("bad_request_error", RootCause.BANK_TEMP_ERROR),
            Map.entry("gateway_error", RootCause.BANK_TEMP_ERROR),
            Map.entry("internal_server_error", RootCause.BANK_TEMP_ERROR)
    );

    private final ClassificationService fallback;

    public FailureClassifierService(ClassificationService fallback) {
        this.fallback = fallback;
    }

    public ClassificationResult classifyFromGatewayReason(String gatewayErrorReason) {
        if (gatewayErrorReason == null || gatewayErrorReason.isBlank()) {
            return new ClassificationResult(RootCause.UNKNOWN, 0.30, METHOD);
        }

        String normalized = gatewayErrorReason.trim().toLowerCase(Locale.ROOT);
        RootCause cause = MAPPING.get(normalized);

        // Check for substring matches if exact key is not found
        if (cause == null) {
            for (Map.Entry<String, RootCause> entry : MAPPING.entrySet()) {
                if (normalized.contains(entry.getKey())) {
                    cause = entry.getValue();
                    break;
                }
            }
        }

        return cause == null
                ? new ClassificationResult(RootCause.UNKNOWN, 0.30, METHOD)
                : new ClassificationResult(cause, 0.99, METHOD);
    }

    public ClassificationResult classifyFromGatewayReason(String gatewayErrorReason, Event fallbackEvent) {
        ClassificationResult mapped = classifyFromGatewayReason(gatewayErrorReason);
        return mapped.getRootCause() == RootCause.UNKNOWN ? fallback.classify(fallbackEvent) : mapped;
    }
}