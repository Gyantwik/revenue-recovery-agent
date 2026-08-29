package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;

    @Column(name = "case_type", length = 30)
    private String caseType;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 5)
    private String currency;

    private Instant timestamp;

    @Column(name = "is_at_risk")
    private Boolean isAtRisk;

    @Column(name = "risk_amount", precision = 10, scale = 2)
    private BigDecimal riskAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_cause", length = 50)
    private RootCause rootCause;

    @Column(name = "classification_confidence", precision = 4, scale = 3)
    private BigDecimal classificationConfidence;

    @Column(name = "signals_used", columnDefinition = "TEXT")
    private String signalsUsed;

    @Column(name = "policy_rule_matched", length = 200)
    private String policyRuleMatched;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", length = 50)
    private ActionTaken actionTaken;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "max_attempts_allowed")
    private Integer maxAttemptsAllowed;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Outcome outcome;

    @Column(name = "recovered_amount", precision = 10, scale = 2)
    private BigDecimal recoveredAmount;

    @Column(name = "stop_or_escalate_reason", length = 200)
    private String stopOrEscalateReason;

    public AuditRecord() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Boolean getIsAtRisk() { return isAtRisk; }
    public void setIsAtRisk(Boolean atRisk) { isAtRisk = atRisk; }
    public BigDecimal getRiskAmount() { return riskAmount; }
    public void setRiskAmount(BigDecimal riskAmount) { this.riskAmount = riskAmount; }
    public RootCause getRootCause() { return rootCause; }
    public void setRootCause(RootCause rootCause) { this.rootCause = rootCause; }
    public BigDecimal getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(BigDecimal classificationConfidence) { this.classificationConfidence = classificationConfidence; }
    public String getSignalsUsed() { return signalsUsed; }
    public void setSignalsUsed(String signalsUsed) { this.signalsUsed = signalsUsed; }
    public void setSignalsUsed(List<String> signals) {
        this.signalsUsed = signals == null ? null : String.join("; ", signals);
    }
    public String getPolicyRuleMatched() { return policyRuleMatched; }
    public void setPolicyRuleMatched(String policyRuleMatched) { this.policyRuleMatched = policyRuleMatched; }
    public ActionTaken getActionTaken() { return actionTaken; }
    public void setActionTaken(ActionTaken actionTaken) { this.actionTaken = actionTaken; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Integer getMaxAttemptsAllowed() { return maxAttemptsAllowed; }
    public void setMaxAttemptsAllowed(Integer maxAttemptsAllowed) { this.maxAttemptsAllowed = maxAttemptsAllowed; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public BigDecimal getRecoveredAmount() { return recoveredAmount; }
    public void setRecoveredAmount(BigDecimal recoveredAmount) { this.recoveredAmount = recoveredAmount; }
    public String getStopOrEscalateReason() { return stopOrEscalateReason; }
    public void setStopOrEscalateReason(String stopOrEscalateReason) { this.stopOrEscalateReason = stopOrEscalateReason; }
}
