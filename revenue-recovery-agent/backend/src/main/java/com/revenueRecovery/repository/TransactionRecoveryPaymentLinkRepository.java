package com.revenueRecovery.repository;

import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRecoveryPaymentLinkRepository
        extends JpaRepository<TransactionRecoveryPaymentLink, Long> {
    boolean existsByEventEventId(String eventId);
    Optional<TransactionRecoveryPaymentLink> findByEventEventId(String eventId);
    Optional<TransactionRecoveryPaymentLink> findByInternalRequestId(String internalRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TransactionRecoveryPaymentLink l join fetch l.event "
            + "where l.internalRequestId = :requestId")
    Optional<TransactionRecoveryPaymentLink> findForUpdateByInternalRequestId(
            @Param("requestId") String requestId);
}
