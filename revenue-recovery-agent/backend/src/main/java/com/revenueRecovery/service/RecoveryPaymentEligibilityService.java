package com.revenueRecovery.service;

import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.RecoveryPaymentLinkRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RecoveryPaymentEligibilityService {
    private static final Set<ActionTaken> ALLOWED_ACTIONS = Set.of(
            ActionTaken.SEND_RECOVERY_LINK, ActionTaken.SEND_ALT_PAYMENT_LINK);

    private final PolicyEngine policyEngine;
    private final RecoveryPaymentLinkRepository linkRepository;

    public RecoveryPaymentEligibilityService(PolicyEngine policyEngine,
            RecoveryPaymentLinkRepository linkRepository) {
        this.policyEngine = policyEngine;
        this.linkRepository = linkRepository;
    }

    public void requireEligibleForNewLink(RecoveryDemoCase recoveryCase) {
        requirePersistedPolicyEligibility(recoveryCase);
        if (linkRepository.existsByRecoveryCaseEventId(recoveryCase.getEventId())) {
            throw RecoveryPaymentException.linkExists();
        }
    }

    public void requireEligibleForFinalization(RecoveryDemoCase recoveryCase) {
        requirePersistedPolicyEligibility(recoveryCase);
    }

    public boolean isEligibleWithoutLinkCheck(RecoveryDemoCase recoveryCase) {
        try {
            requirePersistedPolicyEligibility(recoveryCase);
            return true;
        } catch (RecoveryPaymentException exception) {
            return false;
        }
    }

    private void requirePersistedPolicyEligibility(RecoveryDemoCase recoveryCase) {
        if (recoveryCase == null) throw RecoveryPaymentException.notFound();
        if (recoveryCase.getOutcome() == Outcome.RECOVERED
                || "recovered".equals(recoveryCase.getRecoveryStatus())) {
            throw RecoveryPaymentException.alreadyRecovered();
        }
        if (recoveryCase.getOutcome() == Outcome.ESCALATED
                || recoveryCase.getOutcome() == Outcome.STOPPED_CORRECTLY) {
            throw RecoveryPaymentException.notAllowed();
        }
        PolicyDecision policy = policyEngine.decide(recoveryCase.getRootCause());
        if (!ALLOWED_ACTIONS.contains(policy.getActionTaken())
                || policy.getActionTaken() != recoveryCase.getPolicyAction()) {
            throw RecoveryPaymentException.notAllowed();
        }
        if (recoveryCase.getAmount() == null || recoveryCase.getAmount().signum() <= 0
                || !"INR".equals(recoveryCase.getCurrency())) {
            throw RecoveryPaymentException.notAllowed();
        }
    }
}
