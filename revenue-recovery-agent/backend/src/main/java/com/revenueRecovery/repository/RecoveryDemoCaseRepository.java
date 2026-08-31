package com.revenueRecovery.repository;

import com.revenueRecovery.model.RecoveryDemoCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecoveryDemoCaseRepository extends JpaRepository<RecoveryDemoCase, Long> {
    Optional<RecoveryDemoCase> findByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecoveryDemoCase c where c.eventId = :eventId")
    Optional<RecoveryDemoCase> findForUpdateByEventId(@Param("eventId") String eventId);
}
