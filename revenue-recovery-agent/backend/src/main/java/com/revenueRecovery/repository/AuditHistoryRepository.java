package com.revenueRecovery.repository;

import com.revenueRecovery.model.AuditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditHistoryRepository extends JpaRepository<AuditHistory, Long> {
    List<AuditHistory> findByEventIdOrderByIdAsc(String eventId);
    boolean existsByIdempotencyKey(String idempotencyKey);
    long countByEventId(String eventId);
}
