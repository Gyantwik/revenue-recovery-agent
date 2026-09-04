package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.AgentDecisionTraceResponse;
import com.revenueRecovery.controller.dto.ExplainabilityResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.repository.AgentDecisionTraceRepository;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;

@Service
public class ExplainabilityService {
    private final AuditRecordRepository records;
    private final AuditHistoryRepository history;
    private final AgentDecisionTraceRepository traces;
    public ExplainabilityService(AuditRecordRepository records, AuditHistoryRepository history,
            AgentDecisionTraceRepository traces) { this.records = records; this.history = history; this.traces = traces; }
    public ExplainabilityResponse explain(String eventId) {
        AuditRecord record = records.findByEventId(eventId).orElseThrow(() -> new TransactionNotFoundException(eventId));
        return ExplainabilityResponse.build(record,
                traces.findByEventIdOrderByIdAsc(eventId).stream().map(AgentDecisionTraceResponse::from).toList(),
                history.findByEventIdOrderByIdAsc(eventId));
    }
}
