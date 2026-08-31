package com.revenueRecovery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "razorpay_test_checkout_attempt", uniqueConstraints =
        @UniqueConstraint(name = "uk_test_checkout_order_payment",
                columnNames = {"razorpay_order_id", "razorpay_payment_id"}))
public class RazorpayTestCheckoutAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_request_id", nullable = false, length = 40)
    private String internalRequestId;

    @Column(name = "razorpay_order_id", nullable = false, length = 50)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 50)
    private String razorpayPaymentId;

    // Stored only for later Phase 4C verification; never returned by the intake API.
    @Column(name = "razorpay_signature", length = 512)
    private String razorpaySignature;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(length = 100)
    private String reason;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "verified_at", columnDefinition = "TIMESTAMP")
    private Instant verifiedAt;

    @Column(name = "verification_failure_code", length = 50)
    private String verificationFailureCode;

    public Long getId() { return id; }
    public String getInternalRequestId() { return internalRequestId; }
    public void setInternalRequestId(String internalRequestId) { this.internalRequestId = internalRequestId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public String getRazorpaySignature() { return razorpaySignature; }
    public void setRazorpaySignature(String razorpaySignature) { this.razorpaySignature = razorpaySignature; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerificationFailureCode() { return verificationFailureCode; }
    public void setVerificationFailureCode(String verificationFailureCode) {
        this.verificationFailureCode = verificationFailureCode;
    }
}
