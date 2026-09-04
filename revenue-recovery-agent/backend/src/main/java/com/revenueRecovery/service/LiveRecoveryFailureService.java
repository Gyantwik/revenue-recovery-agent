package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.TransactionRecoveryPaymentLink;
import com.revenueRecovery.model.enums.*;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.TransactionRecoveryPaymentLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveRecoveryFailureService {
    private final TransactionRecoveryPaymentLinkRepository links;
    private final AuditRecordRepository records;
    private final AgentTraceService traces;
    public LiveRecoveryFailureService(TransactionRecoveryPaymentLinkRepository links,
            AuditRecordRepository records, AgentTraceService traces) {
        this.links=links;this.records=records;this.traces=traces;
    }

    @Transactional
    public void recordIfLinked(RazorpayCheckoutEventRequest request) {
        TransactionRecoveryPaymentLink link=links.findForUpdateByInternalRequestId(request.internalRequestId()).orElse(null);
        if(link==null)return;
        AuditRecord record=records.findForUpdateByEventId(link.getEvent().getEventId()).orElse(null);
        if(record==null||record.getOutcome()==Outcome.RECOVERED)return;
        int maximum=record.getMaxAttemptsAllowed()==null?0:record.getMaxAttemptsAllowed();
        int current=record.getAttemptNumber()==null?0:record.getAttemptNumber();
        int next=Math.min(maximum, current+1);
        record.setAttemptNumber(next);record.setGatewayErrorReason(request.reason());
        record.setDetail((record.getDetail()==null?"":record.getDetail())+"; recovery_failure_reason="+request.reason());
        if(maximum==0||next>=maximum){
            record.setLifecycleState(LifecycleState.RETRY_EXHAUSTED);record.setOutcome(Outcome.NOT_RECOVERED);
            record.setStopOrEscalateReason("Maximum retry attempts reached; further retries are blocked");
        }else record.setLifecycleState(LifecycleState.RETRY_SCHEDULED);
        records.save(record);link.setStatus(RecoveryTransactionEligibilityService.ABANDONED);links.save(link);
        traces.append(record.getEventId(),AgentTraceStage.GUARDRAIL_CHECK,"Live retry limit evaluated",
                "attempts="+next+"/"+maximum+"; further_retry="+(next<maximum),AuditActor.RAZORPAY_TEST_VERIFICATION);
        traces.append(record.getEventId(),AgentTraceStage.ACT,"Live retry failed",
                "gateway_error_reason="+request.reason(),AuditActor.RAZORPAY_TEST_VERIFICATION);
    }
}
