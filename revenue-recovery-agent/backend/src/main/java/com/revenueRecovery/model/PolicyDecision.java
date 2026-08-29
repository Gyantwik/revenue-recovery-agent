package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.RootCause;

public class PolicyDecision {

    private final RootCause rootCause;
    private final ActionTaken actionTaken;
    private final int maxAttemptsAllowed;
    private final String policyRuleMatched;

    public PolicyDecision(
            RootCause rootCause,
            ActionTaken actionTaken,
            int maxAttemptsAllowed,
            String policyRuleMatched) {
        this.rootCause = rootCause;
        this.actionTaken = actionTaken;
        this.maxAttemptsAllowed = maxAttemptsAllowed;
        this.policyRuleMatched = policyRuleMatched;
    }

    public RootCause getRootCause() {
        return rootCause;
    }

    public ActionTaken getActionTaken() {
        return actionTaken;
    }

    public int getMaxAttemptsAllowed() {
        return maxAttemptsAllowed;
    }

    public String getPolicyRuleMatched() {
        return policyRuleMatched;
    }
}
