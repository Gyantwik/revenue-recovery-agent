package com.revenueRecovery.repository;

import com.revenueRecovery.model.RazorpayTestCheckoutAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RazorpayTestCheckoutAttemptRepository extends JpaRepository<RazorpayTestCheckoutAttempt, Long> {
    boolean existsByRazorpayOrderIdAndRazorpayPaymentId(String razorpayOrderId, String razorpayPaymentId);
    Optional<RazorpayTestCheckoutAttempt> findByInternalRequestIdAndRazorpayOrderIdAndRazorpayPaymentId(
            String internalRequestId, String razorpayOrderId, String razorpayPaymentId);
    Optional<RazorpayTestCheckoutAttempt> findFirstByRazorpayOrderIdAndStatus(
            String razorpayOrderId, String status);
    Optional<RazorpayTestCheckoutAttempt> findFirstByInternalRequestIdAndRazorpayOrderIdOrderByIdDesc(
            String internalRequestId, String razorpayOrderId);
}
