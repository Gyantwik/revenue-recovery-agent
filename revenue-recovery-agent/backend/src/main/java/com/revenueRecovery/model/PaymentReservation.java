package com.revenueRecovery.model;

import com.revenueRecovery.model.enums.ReservationStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "payment_reservation")
public class PaymentReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="reservation_id", nullable=false, unique=true, length=50) private String reservationId;
    @Column(name="event_id", nullable=false, length=50) private String eventId;
    @Column(name="idempotency_key", nullable=false, unique=true, length=100) private String idempotencyKey;
    @Column(name="order_id", nullable=false, length=50) private String orderId;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal amount;
    @Column(name="customer_ref", length=120) private String customerRef;
    @Column(name="created_at", nullable=false, columnDefinition="TIMESTAMP") private Instant createdAt;
    @Column(name="expires_at", nullable=false, columnDefinition="TIMESTAMP") private Instant expiresAt;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable=false, length=40) private ReservationStatus status;
    @Column(name="fallback_attempts", nullable=false) private Integer fallbackAttempts;
    @Column(name="max_fallback_attempts", nullable=false) private Integer maxFallbackAttempts;
    public Long getId(){return id;} public String getReservationId(){return reservationId;} public void setReservationId(String v){reservationId=v;}
    public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;} public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
    public String getOrderId(){return orderId;} public void setOrderId(String v){orderId=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
    public String getCustomerRef(){return customerRef;} public void setCustomerRef(String v){customerRef=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;} public ReservationStatus getStatus(){return status;} public void setStatus(ReservationStatus v){status=v;}
    public Integer getFallbackAttempts(){return fallbackAttempts;} public void setFallbackAttempts(Integer v){fallbackAttempts=v;} public Integer getMaxFallbackAttempts(){return maxFallbackAttempts;} public void setMaxFallbackAttempts(Integer v){maxFallbackAttempts=v;}
}
