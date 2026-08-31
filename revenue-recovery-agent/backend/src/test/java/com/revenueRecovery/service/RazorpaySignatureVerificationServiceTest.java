package com.revenueRecovery.service;

import com.revenueRecovery.config.RazorpayProperties;
import com.revenueRecovery.repository.RazorpayTestCheckoutAttemptRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.controller.dto.RazorpayPaymentVerificationResponse;
import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RazorpaySignatureVerificationServiceTest {
    private static final String SECRET = "unit_test_secret";
    private static final String EXPECTED =
            "662cb1688d203c2a0d3f1972aeee64a5693a5ec6b6a92584301e9b469f741347";

    @Test
    void knownHmacVectorUsesStoredOrderIdAndLowercaseHex() {
        RazorpaySignatureVerificationService service = service();

        assertEquals(EXPECTED, service.calculateSignature("order_server", "pay_test", SECRET));
        assertTrue(service.verifySignature("order_server", "pay_test", EXPECTED, SECRET));
        assertFalse(service.verifySignature("order_client_controlled", "pay_test", EXPECTED, SECRET));
    }

    @Test
    void tamperedAndMalformedSignaturesAreRejected() {
        RazorpaySignatureVerificationService service = service();

        assertFalse(service.verifySignature("order_server", "pay_tampered", EXPECTED, SECRET));
        assertFalse(service.verifySignature("order_server", "pay_test", "not-hex", SECRET));
    }

    @Test
    void verificationRoutesThroughConstantTimeComparison() {
        TrackingVerificationService service = new TrackingVerificationService();

        assertTrue(service.verifySignature("order_server", "pay_test", EXPECTED, SECRET));
        assertTrue(service.constantTimeComparisonUsed);
    }

    @Test
    void persistenceAndResponseModelsContainNoSecretOrReturnedSignatureField() {
        assertTrue(java.util.Arrays.stream(RazorpayTestCheckoutAttempt.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("secret")));
        assertTrue(java.util.Arrays.stream(RazorpayPaymentVerificationResponse.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("secret")
                        || component.getName().toLowerCase().contains("signature")));
    }

    private RazorpaySignatureVerificationService service() {
        return new RazorpaySignatureVerificationService(new RazorpayProperties(),
                mock(RazorpayTestOrderRepository.class),
                mock(RazorpayTestCheckoutAttemptRepository.class));
    }

    private static final class TrackingVerificationService extends RazorpaySignatureVerificationService {
        private boolean constantTimeComparisonUsed;

        private TrackingVerificationService() {
            super(new RazorpayProperties(), mock(RazorpayTestOrderRepository.class),
                    mock(RazorpayTestCheckoutAttemptRepository.class));
        }

        @Override
        boolean constantTimeEquals(byte[] expected, byte[] received) {
            constantTimeComparisonUsed = true;
            return true;
        }
    }
}
