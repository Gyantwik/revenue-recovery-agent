package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.RootCause;

public class ClassificationResult {

    private final RootCause rootCause;
    private final double confidence;
    private final String method;

    public ClassificationResult(RootCause rootCause, double confidence, String method) {
        this.rootCause = rootCause;
        this.confidence = confidence;
        this.method = method;
    }

    public RootCause getRootCause() {
        return rootCause;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getMethod() {
        return method;
    }
}
