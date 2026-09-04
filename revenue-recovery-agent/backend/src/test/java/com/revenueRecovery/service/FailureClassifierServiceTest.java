package com.revenueRecovery.service;

import com.revenueRecovery.model.enums.RootCause;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureClassifierServiceTest {
    private final FailureClassifierService service = new FailureClassifierService(new ClassificationService());

    @Test void mapsAllDocumentedLiveGatewayReasons() {
        assertCause("gateway_technical_error", RootCause.BANK_TEMP_ERROR);
        assertCause("insufficient_fund", RootCause.INSUFFICIENT_BALANCE);
        assertCause("payment_cancelled", RootCause.USER_CANCELLED);
        assertCause("authentication_failed", RootCause.INCORRECT_PIN);
        assertCause("payment_timed_out", RootCause.WEAK_NETWORK);
    }
    private void assertCause(String reason, RootCause cause) {
        assertEquals(cause, service.classifyFromGatewayReason(reason).getRootCause());
        assertEquals(FailureClassifierService.METHOD, service.classifyFromGatewayReason(reason).getMethod());
    }
}
