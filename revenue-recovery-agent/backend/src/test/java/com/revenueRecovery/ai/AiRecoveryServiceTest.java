package com.revenueRecovery.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRecoveryServiceTest {

    private AuditRecordRepository repository;
    private GeminiClient geminiClient;
    private AiRecoveryService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditRecordRepository.class);
        geminiClient = mock(GeminiClient.class);
        service = new AiRecoveryService(repository, geminiClient, new ObjectMapper());
    }

    @Test
    void fallsBackToStoredClassificationWhenGeminiIsUnavailable() {
        AuditRecord record = record("EVT-1", RootCause.BANK_TEMP_ERROR, ActionTaken.RETRY_PAYMENT);
        when(repository.findByEventId("EVT-1")).thenReturn(Optional.of(record));
        when(geminiClient.generate(anyString(), anyString())).thenReturn(null);

        AiRecoveryDtos.AiAnalysisResponse response = service.analyzeFailure("EVT-1");

        assertThat(response.predictedRootCause()).isEqualTo(RootCause.BANK_TEMP_ERROR);
        assertThat(response.recommendedAction()).isEqualTo(ActionTaken.RETRY_PAYMENT);
        assertThat(response.reasoningTrace()).extracting(AiRecoveryDtos.AiTraceStep::stage)
                .containsExactly("OBSERVE", "CLASSIFY", "DECIDE", "GUARDRAIL_CHECK", "ACT");
    }

    @Test
    void enforcesHardStopEvenWhenModelRecommendsRetry() {
        AuditRecord record = record("EVT-2", RootCause.USER_CANCELLED, ActionTaken.NO_ACTION_STOP);
        when(repository.findByEventId("EVT-2")).thenReturn(Optional.of(record));
        when(geminiClient.generate(anyString(), anyString())).thenReturn("""
                {
                  "predicted_root_cause": "user_cancelled",
                  "confidence": 0.99,
                  "recommended_action": "retry_payment",
                  "ai_explanation": "The customer cancelled.",
                  "optimal_retry_timing": "Immediately",
                  "risk_assessment": "High",
                  "reasoning_trace": [
                    {"stage":"OBSERVE","thought":"Cancellation evidence present.","conclusion":"Customer cancelled."},
                    {"stage":"CLASSIFY","thought":"Matched cancellation evidence.","conclusion":"user_cancelled"},
                    {"stage":"DECIDE","thought":"Considered available actions.","conclusion":"retry_payment"},
                    {"stage":"GUARDRAIL_CHECK","thought":"Checked the stop rule.","conclusion":"Stop is mandatory."},
                    {"stage":"ACT","thought":"Returned the recommendation.","conclusion":"retry_payment"}
                  ]
                }
                """);

        AiRecoveryDtos.AiAnalysisResponse response = service.analyzeFailure("EVT-2");

        assertThat(response.recommendedAction()).isEqualTo(ActionTaken.NO_ACTION_STOP);
    }

    @Test
    void createsSafeOfflineRecoveryMessage() {
        AuditRecord record = record("EVT-3", RootCause.CHECKOUT_ABANDONED, ActionTaken.SEND_RECOVERY_LINK);
        record.setAmount(new BigDecimal("1499.00"));
        record.setCurrency("INR");
        when(repository.findByEventId("EVT-3")).thenReturn(Optional.of(record));
        when(geminiClient.generate(anyString(), anyString())).thenReturn(null);

        AiRecoveryDtos.AiMessageResponse response = service.generateRecoveryMessage(
                "EVT-3", new AiRecoveryDtos.AiMessageRequest("SMS", "Asha"));

        assertThat(response.channel()).isEqualTo("sms");
        assertThat(response.message())
                .contains("Asha")
                .contains("https://checkout.recoverai.demo/pay/EVT-3")
                .contains("not be charged twice");
    }

    @Test
    void recoveredTransactionReturnsResolvedAnalysisWithoutCallingGemini() {
        AuditRecord record = record("EVT-4", RootCause.PAYMENT_PENDING, ActionTaken.VERIFY_STATUS);
        record.setAmount(new BigDecimal("3150.00"));
        record.setOutcome(Outcome.RECOVERED);
        record.setLifecycleState(LifecycleState.RECOVERED_BY_VERIFIED_TEST_PAYMENT);
        when(repository.findByEventId("EVT-4")).thenReturn(Optional.of(record));

        AiRecoveryDtos.AiAnalysisResponse response = service.analyzeFailure("EVT-4");

        assertThat(response.confidence()).isEqualTo(1.0);
        assertThat(response.aiExplanation()).contains("server-verified").contains("successfully recovered");
        assertThat(response.optimalRetryTiming()).startsWith("No retry needed");
        assertThat(response.reasoningTrace()).extracting(AiRecoveryDtos.AiTraceStep::stage)
                .containsExactly("OBSERVE", "CLASSIFY", "DECIDE", "GUARDRAIL_CHECK", "ACT");
        verifyNoInteractions(geminiClient);
    }

    private static AuditRecord record(String eventId, RootCause cause, ActionTaken action) {
        AuditRecord record = new AuditRecord();
        record.setEventId(eventId);
        record.setRootCause(cause);
        record.setActionTaken(action);
        record.setClassificationConfidence(new BigDecimal("0.930"));
        record.setGatewayErrorReason("BANK_TIMEOUT");
        record.setSignalsUsed("gateway_response_code: BANK_TIMEOUT");
        return record;
    }
}
