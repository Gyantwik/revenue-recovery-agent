package com.revenueRecovery.repository;

import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RazorpayTestCheckoutAttemptRepository extends JpaRepository<RazorpayTestCheckoutAttempt, Long> {
    boolean existsByRazorpayOrderIdAndRazorpayPaymentId(String razorpayOrderId, String razorpayPaymentId);
}
