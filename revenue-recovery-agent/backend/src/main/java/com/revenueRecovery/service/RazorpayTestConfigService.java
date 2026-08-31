package com.revenueRecovery.service;

import com.revenueRecovery.config.RazorpayProperties;
import com.revenueRecovery.controller.dto.RazorpayTestConfigResponse;
import org.springframework.stereotype.Service;

@Service
public class RazorpayTestConfigService {
    private final RazorpayProperties properties;

    public RazorpayTestConfigService(RazorpayProperties properties) {
        this.properties = properties;
    }

    public RazorpayTestConfigResponse getCheckoutConfig() {
        String keyId = properties.getKeyId();
        String secret = properties.getKeySecret();
        if (keyId == null || secret == null || keyId.isBlank() || secret.isBlank()
                || !keyId.startsWith("rzp_test_") || keyId.startsWith("rzp_live_")) {
            throw RazorpayServiceException.notConfigured();
        }
        return new RazorpayTestConfigResponse(keyId, "test");
    }
}
