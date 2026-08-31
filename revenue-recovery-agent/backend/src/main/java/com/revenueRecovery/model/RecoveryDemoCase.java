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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recovery_demo_case", uniqueConstraints =
        @UniqueConstraint(name = "uk_recovery_demo_event_id", columnNames = "event_id"))
public class RecoveryDemoCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 50)
    private String eventId;

    @Column(name = "case_type", nullable = false, length = 30)
    private String caseType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "root_cause", nullable = false, length = 50)
    private RootCause rootCause;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "policy_action", nullable = false, length = 50)
    private ActionTaken policyAction;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Outcome outcome;

    @Column(name = "recovery_status", nullable = false, length = 50)
    private String recoveryStatus;

    @Column(name = "demo_only", nullable = false)
    private boolean demoOnly;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "recovered_at", columnDefinition = "TIMESTAMP")
    private Instant recoveredAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public RootCause getRootCause() { return rootCause; }
    public void setRootCause(RootCause rootCause) { this.rootCause = rootCause; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public ActionTaken getPolicyAction() { return policyAction; }
    public void setPolicyAction(ActionTaken policyAction) { this.policyAction = policyAction; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getRecoveryStatus() { return recoveryStatus; }
    public void setRecoveryStatus(String recoveryStatus) { this.recoveryStatus = recoveryStatus; }
    public boolean isDemoOnly() { return demoOnly; }
    public void setDemoOnly(boolean demoOnly) { this.demoOnly = demoOnly; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRecoveredAt() { return recoveredAt; }
    public void setRecoveredAt(Instant recoveredAt) { this.recoveredAt = recoveredAt; }
    public Long getVersion() { return version; }
}
