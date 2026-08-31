package com.revenueRecovery.repository;

import com.revenueRecovery.model.RecoveryPaymentLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecoveryPaymentLinkRepository extends JpaRepository<RecoveryPaymentLink, Long> {
    boolean existsByRecoveryCaseEventId(String eventId);
    Optional<RecoveryPaymentLink> findByRecoveryCaseEventId(String eventId);
    Optional<RecoveryPaymentLink> findByInternalRequestId(String internalRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from RecoveryPaymentLink l join fetch l.recoveryCase where l.internalRequestId = :requestId")
    Optional<RecoveryPaymentLink> findForUpdateByInternalRequestId(@Param("requestId") String requestId);
}
