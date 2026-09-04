package com.revenueRecovery.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.RootCause;

import java.util.List;

public final class AiRecoveryDtos {

    private AiRecoveryDtos() {
    }

    public record AiTraceStep(String stage, String thought, String conclusion) {
    }

    public record AiAnalysisResponse(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("predicted_root_cause") RootCause predictedRootCause,
            double confidence,
            @JsonProperty("recommended_action") ActionTaken recommendedAction,
            @JsonProperty("ai_explanation") String aiExplanation,
            @JsonProperty("optimal_retry_timing") String optimalRetryTiming,
            @JsonProperty("risk_assessment") String riskAssessment,
            @JsonProperty("reasoning_trace") List<AiTraceStep> reasoningTrace,
            @JsonProperty("analysis_source") String analysisSource) {
        public AiAnalysisResponse {
            reasoningTrace = reasoningTrace == null ? List.of() : List.copyOf(reasoningTrace);
        }
    }

    public record AiMessageRequest(String channel, String customerName) {
    }

    public record AiMessageResponse(
            @JsonProperty("event_id") String eventId,
            String channel,
            String subject,
            String message,
            @JsonProperty("suggested_cta") String suggestedCta) {
    }
}
