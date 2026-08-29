package com.revenueRecovery.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "event", uniqueConstraints = @UniqueConstraint(name = "uk_event_event_id", columnNames = "event_id"))
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "case_type")
    private String caseType;

    private BigDecimal amount;

    private String currency;

    private Instant timestamp;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "event_signals", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "signal")
    private List<String> signalsUsed = new ArrayList<>();

    public Event() {
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
    public List<String> getSignalsUsed() { return signalsUsed; }
    public void setSignalsUsed(List<String> signalsUsed) {
        this.signalsUsed = signalsUsed == null ? new ArrayList<>() : new ArrayList<>(signalsUsed);
    }
}
