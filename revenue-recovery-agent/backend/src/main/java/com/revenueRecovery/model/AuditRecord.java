package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.TransactionSource;
import com.revenueRecovery.model.enums.VerificationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "audit_record")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 50)
    private String eventId;

    @Column(name = "case_type", length = 30)
    private String caseType;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 5)
    private String currency;

    @Column(columnDefinition = "TIMESTAMP")
    private Instant timestamp;

    @Column(name = "is_at_risk")
    private Boolean isAtRisk;

    @Column(name = "risk_amount", precision = 10, scale = 2)
    private BigDecimal riskAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "root_cause", length = 50)
    private RootCause rootCause;

    @Column(name = "classification_confidence", precision = 4, scale = 3)
    private BigDecimal classificationConfidence;

    @Column(name = "signals_used", columnDefinition = "TEXT")
    private String signalsUsed;

    @Column(name = "policy_rule_matched", length = 200)
    private String policyRuleMatched;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "action_taken", length = 50)
    private ActionTaken actionTaken;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "max_attempts_allowed")
    private Integer maxAttemptsAllowed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private Outcome outcome;

    @Column(name = "recovered_amount", precision = 10, scale = 2)
    private BigDecimal recoveredAmount;

    @Column(name = "stop_or_escalate_reason", length = 200)
    private String stopOrEscalateReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    // Nullable at the schema level so Hibernate can upgrade an existing Phase 2 H2 table;
    // the pipeline immediately backfills and persists a state for every processed record.
    @Column(name = "lifecycle_state", length = 50)
    private LifecycleState lifecycleState = LifecycleState.RECEIVED;

    @Column(name = "next_eligible_action_at", columnDefinition = "TIMESTAMP")
    private Instant nextEligibleActionAt;

    @Column(name = "recovery_window_expires_at", columnDefinition = "TIMESTAMP")
    private Instant recoveryWindowExpiresAt;

    @Column(name = "customer_ref", length = 120)
    private String customerRef;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", length = 30)
    private TransactionSource source;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "verification_result", length = 50)
    private VerificationResult verificationResult;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "gateway_error_reason", length = 100)
    private String gatewayErrorReason;

    @Column(name = "gateway_order_id", length = 50)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 50)
    private String gatewayPaymentId;

    @Column(name = "escalation_reason", length = 100)
    private String escalationReason;

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
    public LifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(LifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }
    public Instant getNextEligibleActionAt() { return nextEligibleActionAt; }
    public void setNextEligibleActionAt(Instant nextEligibleActionAt) { this.nextEligibleActionAt = nextEligibleActionAt; }
    public Instant getRecoveryWindowExpiresAt() { return recoveryWindowExpiresAt; }
    public void setRecoveryWindowExpiresAt(Instant recoveryWindowExpiresAt) { this.recoveryWindowExpiresAt = recoveryWindowExpiresAt; }
    public String getCustomerRef() { return customerRef; }
    public void setCustomerRef(String customerRef) { this.customerRef = customerRef; }
    public TransactionSource getSource() { return source; }
    public void setSource(TransactionSource source) { this.source = source; }
    public VerificationResult getVerificationResult() { return verificationResult; }
    public void setVerificationResult(VerificationResult verificationResult) { this.verificationResult = verificationResult; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getGatewayErrorReason() { return gatewayErrorReason; }
    public void setGatewayErrorReason(String gatewayErrorReason) { this.gatewayErrorReason = gatewayErrorReason; }
    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }
    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
}
