package com.revenueRecovery.repository;

import com.revenueRecovery.model.RazorpayTestOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RazorpayTestOrderRepository extends JpaRepository<RazorpayTestOrder, Long> {
    Optional<RazorpayTestOrder> findByInternalRequestId(String internalRequestId);
}
