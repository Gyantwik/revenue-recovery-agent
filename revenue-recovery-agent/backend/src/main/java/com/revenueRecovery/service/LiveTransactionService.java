package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.model.*;
import com.revenueRecovery.model.enums.*;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class LiveTransactionService {
    private static final Set<String> EXPLICIT_DEMO_REASONS = Set.of(
            "insufficient_fund",
            "authentication_failed",
            "gateway_technical_error",
            "payment_timed_out",
            "payment_cancelled");

    private final AuditRecordRepository records;
    private final EventRepository events;
    private final FailureClassifierService classifier;
    private final PolicyEngine policyEngine;
    private final AgentTraceService traces;

    public LiveTransactionService(AuditRecordRepository records, EventRepository events,
            FailureClassifierService classifier, PolicyEngine policyEngine, AgentTraceService traces) {
        this.records = records; this.events = events; this.classifier = classifier;
        this.policyEngine = policyEngine; this.traces = traces;
    }

    @Transactional
    public synchronized AuditRecord record(RazorpayTestOrder order, RazorpayCheckoutEventRequest request) {
        AuditRecord existing = records.findByGatewayOrderId(order.getRazorpayOrderId()).orElse(null);
        if (existing != null) return existing;

        String gatewayReason = normalizeReason(request);
        Event event = new Event();
        event.setEventId(nextEventId());
        event.setCaseType("payment_degradation");
        event.setAmount(order.getAmountInr()); event.setCurrency(order.getCurrency());
        event.setTimestamp(Instant.now());
        event.setSignalsUsed(List.of("gateway_error_reason:" + safe(gatewayReason),
                "error_code:" + safe(request.errorCode()), "latency_ms:" + value(request.latencyMs())));
        events.save(event);

        boolean successCallback = RazorpayCheckoutEventService.SUCCESS.equals(request.eventType());
        ClassificationResult classification = successCallback
                ? new ClassificationResult(RootCause.UNKNOWN, 1.0, FailureClassifierService.METHOD)
                : classifier.classifyFromGatewayReason(gatewayReason, event);
        RootCause cause = classification.getConfidence() < 0.75 ? RootCause.UNKNOWN : classification.getRootCause();
        PolicyDecision policy = policyEngine.decide(cause);

        AuditRecord record = new AuditRecord();
        record.setEventId(event.getEventId()); record.setCaseType(event.getCaseType());
        record.setAmount(event.getAmount()); record.setCurrency(event.getCurrency());
        record.setTimestamp(event.getTimestamp()); record.setIsAtRisk(!successCallback);
        record.setRiskAmount(successCallback ? BigDecimal.ZERO : event.getAmount());
        record.setRootCause(cause); record.setClassificationConfidence(BigDecimal.valueOf(classification.getConfidence()));
        record.setSignalsUsed(event.getSignalsUsed()); record.setPolicyRuleMatched(policy.getPolicyRuleMatched());
        record.setActionTaken(policy.getActionTaken()); record.setAttemptNumber(successCallback ? 0 : Math.min(1, policy.getMaxAttemptsAllowed()));
        record.setMaxAttemptsAllowed(policy.getMaxAttemptsAllowed());
        record.setCustomerRef(blankToDefault(request.customerRef())); record.setSource(TransactionSource.LIVE);
        record.setGatewayErrorReason(gatewayReason); record.setGatewayOrderId(order.getRazorpayOrderId());
        record.setGatewayPaymentId(request.razorpayPaymentId());
        record.setDetail(detail(request, gatewayReason));

        if (successCallback) {
            applySafelySettledState(record);
        } else if (policy.getActionTaken() == ActionTaken.NO_ACTION_STOP) {
            record.setOutcome(Outcome.STOPPED_CORRECTLY); record.setRecoveredAmount(BigDecimal.ZERO);
            record.setLifecycleState(LifecycleState.STOPPED);
            record.setStopOrEscalateReason(cause == RootCause.USER_CANCELLED
                    ? "Customer actively cancelled this payment. Per policy, automated recovery is not offered."
                    : "Security-related authentication stop; automated recovery is not permitted.");
        } else if (policy.getActionTaken() == ActionTaken.ESCALATE_MERCHANT) {
            record.setOutcome(Outcome.ESCALATED); record.setRecoveredAmount(BigDecimal.ZERO);
            record.setLifecycleState(LifecycleState.ESCALATED);
            record.setStopOrEscalateReason("Manual review required for an unmapped gateway failure.");
        } else {
            record.setOutcome(Outcome.NOT_RECOVERED); record.setRecoveredAmount(BigDecimal.ZERO);
            record.setLifecycleState(policy.getActionTaken() == ActionTaken.SEND_ALT_PAYMENT_LINK
                    ? LifecycleState.RECOVERY_LINK_SENT : policy.getActionTaken() == ActionTaken.VERIFY_STATUS
                    ? LifecycleState.VERIFYING_PAYMENT : LifecycleState.RETRY_SCHEDULED);
        }
        record = records.save(record);
        traces.createInitialTrace(record, classification.getMethod(), AuditActor.RAZORPAY_TEST_VERIFICATION);
        return record;
    }

    @Transactional
    public AuditRecord markVerified(String orderId, String paymentId) {
        AuditRecord record = records.findByGatewayOrderId(orderId).orElse(null);
        if (record == null || record.getOutcome() == Outcome.RECOVERED) return record;
        record.setGatewayPaymentId(paymentId);
        applySafelySettledState(record);
        record.setLifecycleState(LifecycleState.RECOVERED_BY_VERIFIED_TEST_PAYMENT);
        record.setDetail(record.getDetail() + "; verified_payment_id=" + paymentId);
        AuditRecord saved = records.save(record);
        traces.append(record.getEventId(), AgentTraceStage.ACT, "Payment verified and transaction recovered",
                "payment_id=" + paymentId + "; idempotency=order_id", AuditActor.RAZORPAY_TEST_VERIFICATION);
        return saved;
    }

    private String nextEventId() {
        int max = records.findAll().stream().map(AuditRecord::getEventId).filter(java.util.Objects::nonNull)
                .filter(id -> id.matches("TXN\\d+"))
                .mapToInt(id -> Integer.parseInt(id.substring(3))).max().orElse(10065);
        return "TXN" + Math.max(10066, max + 1);
    }

    /** A verified Checkout payment never enters recovery, so it has no failure policy. */
    private void applySafelySettledState(AuditRecord record) {
        record.setIsAtRisk(false); record.setRiskAmount(BigDecimal.ZERO);
        record.setRootCause(RootCause.UNKNOWN); record.setClassificationConfidence(BigDecimal.ONE);
        record.setPolicyRuleMatched("Settled payment screening → No recovery required");
        record.setActionTaken(ActionTaken.VERIFY_STATUS); record.setAttemptNumber(0); record.setMaxAttemptsAllowed(0);
        record.setOutcome(Outcome.RECOVERED); record.setRecoveredAmount(record.getAmount());
        record.setLifecycleState(LifecycleState.RECOVERED); record.setVerificationResult(VerificationResult.CONFIRMED_SUCCESS);
        record.setEscalationReason(null); record.setStopOrEscalateReason(null);
    }

    private String normalizeReason(RazorpayCheckoutEventRequest request) {
        String value = request.reason();
        if (value != null) value = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (value == null || value.isBlank()) value = RazorpayCheckoutEventService.DISMISSED.equals(request.eventType())
                ? "payment_cancelled" : null;
        if (value != null && EXPLICIT_DEMO_REASONS.contains(value)) return value;
        if ((Boolean.TRUE.equals(request.simulatedConnection()) || value(request.latencyMs()) > 1200)
                && !"payment_cancelled".equals(value)) value = "payment_timed_out";
        return value;
    }
    private String detail(RazorpayCheckoutEventRequest r, String reason) {
        return "{\"gateway_error_reason\":\"" + safe(reason) + "\",\"payment_id\":\""
                + safe(r.razorpayPaymentId()) + "\",\"error_code\":\"" + safe(r.errorCode())
                + "\",\"error_description\":\"" + safe(r.errorDescription()) + "\",\"error_source\":\""
                + safe(r.errorSource()) + "\",\"error_step\":\"" + safe(r.errorStep())
                + "\",\"latency_ms\":" + value(r.latencyMs()) + "}";
    }
    private String safe(String value) { return value == null ? "" : value.replace("\"", "'"); }
    private long value(Long value) { return value == null ? 0 : value; }
    private String blankToDefault(String value) { return value == null || value.isBlank() ? "Checkout customer" : value.trim(); }
}
