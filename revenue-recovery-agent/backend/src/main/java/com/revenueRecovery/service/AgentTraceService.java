package com.revenueRecovery.service;

import com.revenueRecovery.model.AgentDecisionTrace;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.AgentTraceStage;
import com.revenueRecovery.model.enums.AuditActor;
import com.revenueRecovery.repository.AgentDecisionTraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AgentTraceService {
    private final AgentDecisionTraceRepository repository;

    public AgentTraceService(AgentDecisionTraceRepository repository) { this.repository = repository; }

    @Transactional
    public void createInitialTrace(AuditRecord record, String method, AuditActor actor) {
        if (repository.existsByEventId(record.getEventId())) return;
        add(record.getEventId(), AgentTraceStage.OBSERVE, "Raw payment signals observed",
                "signals=" + safe(record.getSignalsUsed()) + "; gateway_error_reason="
                        + safe(record.getGatewayErrorReason()), actor);
        add(record.getEventId(), AgentTraceStage.CLASSIFY, "Root cause classified",
                "root_cause=" + record.getRootCause() + "; confidence="
                        + record.getClassificationConfidence() + "; method=" + method, actor);
        add(record.getEventId(), AgentTraceStage.DECIDE, "Policy decision selected",
                "action=" + record.getActionTaken() + "; rule=" + safe(record.getPolicyRuleMatched()), actor);
        add(record.getEventId(), AgentTraceStage.GUARDRAIL_CHECK, "Safety guardrails evaluated",
                "attempts=" + record.getAttemptNumber() + "/" + record.getMaxAttemptsAllowed()
                        + "; idempotency=checked; terminal_state=checked", actor);
        add(record.getEventId(), AgentTraceStage.ACT, "Permitted action recorded",
                "outcome=" + record.getOutcome() + "; lifecycle=" + record.getLifecycleState(), actor);
    }

    @Transactional
    public void append(String eventId, AgentTraceStage stage, String summary, String detail, AuditActor actor) {
        add(eventId, stage, summary, detail, actor);
    }

    @Transactional(readOnly = true)
    public List<AgentDecisionTrace> get(String eventId) {
        return repository.findByEventIdOrderByIdAsc(eventId);
    }

    private void add(String eventId, AgentTraceStage stage, String summary, String detail, AuditActor actor) {
        AgentDecisionTrace trace = new AgentDecisionTrace();
        trace.setEventId(eventId); trace.setStage(stage); trace.setSummary(summary);
        trace.setDetail(detail); trace.setActor(actor); trace.setTimestamp(Instant.now());
        repository.save(trace);
    }

    private String safe(String value) { return value == null ? "null" : value; }
}
