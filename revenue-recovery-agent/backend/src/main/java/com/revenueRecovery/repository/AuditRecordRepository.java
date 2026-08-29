package com.revenueRecovery.repository;

import com.revenueRecovery.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    Optional<AuditRecord> findByEventId(String eventId);
}
