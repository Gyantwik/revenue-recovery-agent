package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.RecoveryCheckoutAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_recovery_payment_link", uniqueConstraints = {
        @UniqueConstraint(name = "uk_txn_recovery_link_event", columnNames = "event_id"),
        @UniqueConstraint(name = "uk_txn_recovery_link_request", columnNames = "internal_request_id"),
        @UniqueConstraint(name = "uk_txn_recovery_link_order", columnNames = "razorpay_order_id"),
        @UniqueConstraint(name = "uk_txn_recovery_link_payment", columnNames = "razorpay_payment_id")
})
public class TransactionRecoveryPaymentLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", referencedColumnName = "event_id", nullable = false, unique = true)
    private Event event;

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

    @Column(nullable = false, length = 40)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_action", nullable = false, length = 40)
    private RecoveryCheckoutAction recoveryAction;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "verified_at", columnDefinition = "TIMESTAMP")
    private Instant verifiedAt;

    @Column(name = "recovered_at", columnDefinition = "TIMESTAMP")
    private Instant recoveredAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
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
    public RecoveryCheckoutAction getRecoveryAction() { return recoveryAction; }
    public void setRecoveryAction(RecoveryCheckoutAction recoveryAction) { this.recoveryAction = recoveryAction; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getRecoveredAt() { return recoveredAt; }
    public void setRecoveredAt(Instant recoveredAt) { this.recoveredAt = recoveredAt; }
    public Long getVersion() { return version; }
}
