package com.revenueRecovery.model;

import java.math.BigDecimal;

public class DetectionResult {

    private final boolean atRisk;
    private final BigDecimal riskAmount;

    public DetectionResult(boolean atRisk, BigDecimal riskAmount) {
        this.atRisk = atRisk;
        this.riskAmount = riskAmount;
    }

    public boolean isAtRisk() {
        return atRisk;
    }

    public BigDecimal getRiskAmount() {
        return riskAmount;
    }
}
