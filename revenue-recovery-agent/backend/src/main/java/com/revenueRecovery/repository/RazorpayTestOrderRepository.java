package com.revenueRecovery.repository;

import com.revenueRecovery.model.RazorpayTestOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface RazorpayTestOrderRepository extends JpaRepository<RazorpayTestOrder, Long> {
    Optional<RazorpayTestOrder> findByInternalRequestId(String internalRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RazorpayTestOrder o where o.internalRequestId = :internalRequestId")
    Optional<RazorpayTestOrder> findForUpdateByInternalRequestId(
            @Param("internalRequestId") String internalRequestId);
}
