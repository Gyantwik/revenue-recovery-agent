package com.revenueRecovery.repository;

import com.revenueRecovery.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
    Optional<AuditRecord> findByEventId(String eventId);
    Optional<AuditRecord> findByGatewayOrderId(String gatewayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuditRecord a where a.eventId = :eventId")
    Optional<AuditRecord> findForUpdateByEventId(@Param("eventId") String eventId);
}
