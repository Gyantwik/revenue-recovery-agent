package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.PaymentReservation;
import com.revenueRecovery.model.enums.*;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.PaymentReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class ReservationService {
    private final PaymentReservationRepository reservations;
    private final AuditRecordRepository records;
    private final AgentTraceService traces;

    public ReservationService(PaymentReservationRepository reservations, AuditRecordRepository records,
            AgentTraceService traces) {
        this.reservations = reservations;
        this.records = records;
        this.traces = traces;
    }

    @Transactional
    public PaymentReservation create(String eventId) {
        AuditRecord record = records.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new TransactionNotFoundException(eventId));
        if (record.getRootCause() != RootCause.WEAK_NETWORK) {
            throw new IllegalArgumentException("Reservations are only available for weak-network cases");
        }
        int attempts = record.getAttemptNumber() == null ? 0 : record.getAttemptNumber();
        int max = record.getMaxAttemptsAllowed() == null ? 0 : record.getMaxAttemptsAllowed();
        if (max == 0 || attempts >= max) {
            throw new IllegalStateException("Attempt limit reached; reservation is blocked");
        }
        String reservationOrderId = record.getGatewayOrderId() == null
                ? "seeded:" + eventId : record.getGatewayOrderId();
        PaymentReservation existing = reservations.findFirstByOrderIdAndStatus(
                reservationOrderId, ReservationStatus.PENDING).orElse(null);
        if (existing != null) return existing;

        PaymentReservation item = new PaymentReservation();
        String uuid = UUID.randomUUID().toString();
        item.setReservationId("RSV-" + uuid);
        item.setIdempotencyKey("reservation:" + reservationOrderId);
        item.setEventId(eventId);
        item.setOrderId(reservationOrderId);
        item.setAmount(record.getAmount());
        item.setCustomerRef(record.getCustomerRef());
        item.setCreatedAt(Instant.now());
        item.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        item.setStatus(ReservationStatus.PENDING);
        item.setFallbackAttempts(0);
        item.setMaxFallbackAttempts(1);
        item = reservations.save(item);
        traces.append(eventId, AgentTraceStage.ACT, "Weak-network payment reserved",
                "reservation_id=" + item.getReservationId(), AuditActor.RESERVATION_SYSTEM);
        return item;
    }

    @Transactional
    public PaymentReservation reconnect(String eventId) {
        PaymentReservation item = latest(eventId);
        if (item.getStatus() != ReservationStatus.PENDING) return item;

        item.setStatus(ReservationStatus.COMPLETED);
        AuditRecord record = records.findForUpdateByEventId(eventId).orElseThrow();
        if (record.getOutcome() != Outcome.RECOVERED) {
            record.setOutcome(Outcome.RECOVERED);
            record.setRecoveredAmount(record.getAmount());
            record.setLifecycleState(LifecycleState.RECOVERED_BY_VERIFIED_TEST_PAYMENT);
            record.setStopOrEscalateReason(null);
            record.setEscalationReason(null);
            records.save(record);
        }
        traces.append(eventId, AgentTraceStage.ACT,
                "Reservation completed: payment confirmed on network reconnect",
                "idempotency_key=" + item.getIdempotencyKey(), AuditActor.RESERVATION_SYSTEM);
        return reservations.save(item);
    }

    @Transactional
    public PaymentReservation expire(String eventId) {
        PaymentReservation item = latest(eventId);
        if (item.getStatus() != ReservationStatus.PENDING) return item;

        item.setFallbackAttempts(item.getFallbackAttempts() + 1);
        AuditRecord record = records.findForUpdateByEventId(eventId).orElseThrow();

        if (item.getFallbackAttempts() > item.getMaxFallbackAttempts()) {
            item.setStatus(ReservationStatus.ESCALATED);
            record.setOutcome(Outcome.ESCALATED);
            record.setLifecycleState(LifecycleState.ESCALATED);
            record.setEscalationReason("RESERVATION_FALLBACK_EXHAUSTED");
            record.setStopOrEscalateReason("Reservation and alternative-payment fallback attempts exhausted");
        } else {
            item.setStatus(ReservationStatus.EXPIRED_ALT_LINK_SENT);
            record.setActionTaken(ActionTaken.SEND_ALT_PAYMENT_LINK);
            record.setLifecycleState(LifecycleState.RECOVERY_LINK_SENT);

            // FIX: Align attempt tracking and policy rule to avoid "1/2 attempts" display
            record.setAttemptNumber(1);
            record.setMaxAttemptsAllowed(1);
            record.setPolicyRuleMatched("Weak network reservation expired → Alternative payment link sent");
        }

        records.save(record);
        traces.append(eventId, AgentTraceStage.ACT,
                "Reservation expiry fallback executed",
                "status=" + item.getStatus(), AuditActor.RESERVATION_SYSTEM);
        return reservations.save(item);
    }

    @Transactional
    public PaymentReservation escalateFallback(String eventId) {
        PaymentReservation item = latest(eventId);
        if (item.getStatus() == ReservationStatus.COMPLETED || item.getStatus() == ReservationStatus.ESCALATED) {
            return item;
        }

        item.setFallbackAttempts(item.getMaxFallbackAttempts() + 1);
        item.setStatus(ReservationStatus.ESCALATED);
        AuditRecord record = records.findForUpdateByEventId(eventId).orElseThrow();
        record.setOutcome(Outcome.ESCALATED);
        record.setLifecycleState(LifecycleState.ESCALATED);
        record.setEscalationReason("RESERVATION_FALLBACK_EXHAUSTED");
        record.setStopOrEscalateReason("Reservation and alternative-payment fallback attempts exhausted");
        records.save(record);
        traces.append(eventId, AgentTraceStage.ACT, "Reservation fallback escalated",
                "Fallback cap reached", AuditActor.MERCHANT_MANUAL);
        return reservations.save(item);
    }

    @Scheduled(fixedDelayString = "${recovery.reservation-scan-ms:60000}")
    @Transactional
    public void expireDueReservations() {
        for (PaymentReservation item : reservations.findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, Instant.now())) {
            expire(item.getEventId());
        }
    }

    public PaymentReservation latest(String eventId) {
        return reservations.findFirstByEventIdOrderByIdDesc(eventId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found"));
    }
}
