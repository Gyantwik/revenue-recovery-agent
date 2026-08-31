package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.model.enums.LifecycleState;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "audit_history", indexes = @Index(name = "idx_history_event_id", columnList = "event_id"))
public class AuditHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, length = 50) private String eventId;
    @Column(nullable = false, columnDefinition = "TIMESTAMP") private Instant timestamp;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "previous_state", length = 30) private LifecycleState previousState;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "new_state", nullable = false, length = 30) private LifecycleState newState;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "root_cause", length = 50) private RootCause rootCause;
    @Column(name = "classification_confidence", precision = 4, scale = 3) private BigDecimal classificationConfidence;
    @Column(name = "policy_rule_matched", length = 200) private String policyRuleMatched;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "action_taken", length = 50) private ActionTaken actionTaken;
    @Column(name = "attempt_number") private Integer attemptNumber;
    @Column(name = "max_attempts_allowed") private Integer maxAttemptsAllowed;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(name = "outcome_if_terminal", length = 30) private Outcome outcomeIfTerminal;
    @Column(length = 500) private String reason;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30) private AuditActor actor;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150) private String idempotencyKey;

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public LifecycleState getPreviousState() { return previousState; }
    public void setPreviousState(LifecycleState previousState) { this.previousState = previousState; }
    public LifecycleState getNewState() { return newState; }
    public void setNewState(LifecycleState newState) { this.newState = newState; }
    public RootCause getRootCause() { return rootCause; }
    public void setRootCause(RootCause rootCause) { this.rootCause = rootCause; }
    public BigDecimal getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(BigDecimal classificationConfidence) { this.classificationConfidence = classificationConfidence; }
    public String getPolicyRuleMatched() { return policyRuleMatched; }
    public void setPolicyRuleMatched(String policyRuleMatched) { this.policyRuleMatched = policyRuleMatched; }
    public ActionTaken getActionTaken() { return actionTaken; }
    public void setActionTaken(ActionTaken actionTaken) { this.actionTaken = actionTaken; }
    public Integer getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }
    public Integer getMaxAttemptsAllowed() { return maxAttemptsAllowed; }
    public void setMaxAttemptsAllowed(Integer maxAttemptsAllowed) { this.maxAttemptsAllowed = maxAttemptsAllowed; }
    public Outcome getOutcomeIfTerminal() { return outcomeIfTerminal; }
    public void setOutcomeIfTerminal(Outcome outcomeIfTerminal) { this.outcomeIfTerminal = outcomeIfTerminal; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public AuditActor getActor() { return actor; }
    public void setActor(AuditActor actor) { this.actor = actor; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
