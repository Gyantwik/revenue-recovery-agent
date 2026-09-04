package com.revenueRecovery.service;

import com.revenueRecovery.model.DetectionResult;
import com.revenueRecovery.model.Event;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class DetectionService {

    public DetectionResult detect(Event event) {
        List<String> signals = event.getSignalsUsed() == null ? List.of() : event.getSignalsUsed();
        boolean safelySettled = signals.stream().filter(java.util.Objects::nonNull)
                .map(signal -> signal.toUpperCase(Locale.ROOT))
                .anyMatch(signal -> signal.contains("PAYMENT_SETTLED") || signal.contains("SUCCESS_CONFIRMED")
                        || signal.contains("DUPLICATE_WEBHOOK"));
        if (safelySettled) return new DetectionResult(false, BigDecimal.ZERO);
        return new DetectionResult(true, event.getAmount());
    }
}
