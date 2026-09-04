package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.RootCause;
import java.math.BigDecimal;

public record PolicyImpactResponse(@JsonProperty("root_cause") RootCause rootCause,
        @JsonProperty("policy_rule") String policyRule, @JsonProperty("allowed_action") ActionTaken allowedAction,
        @JsonProperty("eligible_cases") int eligibleCases, @JsonProperty("recoverable_amount") BigDecimal recoverableAmount,
        @JsonProperty("expected_recovery_rate") BigDecimal expectedRecoveryRate,
        @JsonProperty("cases_blocked_for_safety") int casesBlockedForSafety,
        @JsonProperty("blocked_amount") BigDecimal blockedAmount,
        @JsonProperty("at_risk_cases") int atRiskCases,
        @JsonProperty("not_at_risk_cases") int notAtRiskCases,
        @JsonProperty("total_cases_considered") int totalCasesConsidered) { }
