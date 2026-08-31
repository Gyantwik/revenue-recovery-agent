package com.revenueRecovery.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recovery_payment_link", uniqueConstraints = {
        @UniqueConstraint(name = "uk_recovery_link_event", columnNames = "recovery_event_id"),
        @UniqueConstraint(name = "uk_recovery_link_internal_request", columnNames = "internal_request_id"),
        @UniqueConstraint(name = "uk_recovery_link_order", columnNames = "razorpay_order_id"),
        @UniqueConstraint(name = "uk_recovery_link_payment", columnNames = "razorpay_payment_id")
})
public class RecoveryPaymentLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recovery_event_id", referencedColumnName = "event_id", nullable = false, unique = true)
    private RecoveryDemoCase recoveryCase;

    @Column(name = "internal_request_id", nullable = false, unique = true, length = 40)
    private String internalRequestId;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 50)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 50)
    private String razorpayPaymentId;

    @Column(name = "amount_inr", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountInr;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 10)
    private String mode;

    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "verified_at", columnDefinition = "TIMESTAMP")
    private Instant verifiedAt;

    @Column(name = "recovered_at", columnDefinition = "TIMESTAMP")
    private Instant recoveredAt;

    public Long getId() { return id; }
    public RecoveryDemoCase getRecoveryCase() { return recoveryCase; }
    public void setRecoveryCase(RecoveryDemoCase recoveryCase) { this.recoveryCase = recoveryCase; }
    public String getInternalRequestId() { return internalRequestId; }
    public void setInternalRequestId(String internalRequestId) { this.internalRequestId = internalRequestId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public BigDecimal getAmountInr() { return amountInr; }
    public void setAmountInr(BigDecimal amountInr) { this.amountInr = amountInr; }
    public Long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(Long amountPaise) { this.amountPaise = amountPaise; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getRecoveredAt() { return recoveredAt; }
    public void setRecoveredAt(Instant recoveredAt) { this.recoveredAt = recoveredAt; }
}
