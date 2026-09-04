package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.AgentTraceStage;
import com.revenueRecovery.model.enums.AuditActor;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "agent_decision_trace", indexes =
        @Index(name = "idx_agent_trace_event", columnList = "event_id"))
public class AgentDecisionTrace {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private AgentTraceStage stage;
    @Column(nullable = false, length = 200)
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String detail;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 40)
    private AuditActor actor;
    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    private Instant timestamp;

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public AgentTraceStage getStage() { return stage; }
    public void setStage(AgentTraceStage stage) { this.stage = stage; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public AuditActor getActor() { return actor; }
    public void setActor(AuditActor actor) { this.actor = actor; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
