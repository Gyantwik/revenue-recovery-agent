package com.revenueRecovery.service;

import com.revenueRecovery.config.RazorpayProperties;
import com.revenueRecovery.controller.dto.CreateRazorpayTestOrderRequest;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RazorpayConfigurationValidationTest {
    @Test
    void rejectsLiveKeyIdWithoutCallingUpstream() {
        assertConfigurationRejected("rzp_" + "live_placeholder");
    }

    @Test
    void rejectsNonTestKeyIdWithoutCallingUpstream() {
        assertConfigurationRejected("invalid_placeholder");
    }

    private void assertConfigurationRejected(String keyId) {
        RazorpayProperties properties = new RazorpayProperties();
        properties.setKeyId(keyId);
        properties.setKeySecret("safe-unit-placeholder");
        RestTemplate restTemplate = mock(RestTemplate.class);
        RazorpayTestOrderRepository repository = mock(RazorpayTestOrderRepository.class);
        RazorpayOrderService service = new RazorpayOrderService(properties, restTemplate, repository);

        RazorpayServiceException exception = assertThrows(RazorpayServiceException.class,
                () -> service.createTestOrder(new CreateRazorpayTestOrderRequest(
                        new BigDecimal("500.00"), "INR")));

        assertEquals("Razorpay Test Mode is not configured.", exception.getMessage());
        verifyNoInteractions(restTemplate, repository);
    }
}
