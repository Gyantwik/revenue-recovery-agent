package com.revenueRecovery.repository;

import com.revenueRecovery.model.AgentDecisionTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentDecisionTraceRepository extends JpaRepository<AgentDecisionTrace, Long> {
    List<AgentDecisionTrace> findByEventIdOrderByIdAsc(String eventId);
    boolean existsByEventId(String eventId);
}
