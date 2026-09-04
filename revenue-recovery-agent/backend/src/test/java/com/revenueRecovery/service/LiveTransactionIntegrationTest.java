package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.AgentDecisionTrace;
import com.revenueRecovery.model.PaymentReservation;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.model.enums.*;
import com.revenueRecovery.repository.AgentDecisionTraceRepository;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:live-transaction-test;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
@Transactional
class LiveTransactionIntegrationTest {
    @Autowired LiveTransactionService live;
    @Autowired AgentDecisionTraceRepository traces;
    @Autowired AuditRecordRepository records;
    @Autowired ReservationService reservations;

    @Test void fiveGatewayOutcomesCreateDistinctClassifiedLiveTransactionsAndTraces() {
        Object[][] rows={{"gateway_technical_error",RootCause.BANK_TEMP_ERROR,ActionTaken.RETRY_PAYMENT},
                {"insufficient_fund",RootCause.INSUFFICIENT_BALANCE,ActionTaken.SEND_ALT_PAYMENT_LINK},
                {"payment_cancelled",RootCause.USER_CANCELLED,ActionTaken.NO_ACTION_STOP},
                {"authentication_failed",RootCause.INCORRECT_PIN,ActionTaken.NO_ACTION_STOP},
                {"payment_timed_out",RootCause.WEAK_NETWORK,ActionTaken.RETRY_PAYMENT}};
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(int i=0;i<rows.length;i++){
            RazorpayTestOrder order=order(i);
            AuditRecord record=live.record(order,request(order,(String)rows[i][0]));
            assertEquals(rows[i][1],record.getRootCause());assertEquals(rows[i][2],record.getActionTaken());
            assertEquals(TransactionSource.LIVE,record.getSource());assertTrue(ids.add(record.getEventId()));
            assertEquals(5,traces.findByEventIdOrderByIdAsc(record.getEventId()).size());
        }
    }

    @Test void reconnectCompletesReservationAndRecoversTransaction() {
        RazorpayTestOrder order=order(99);AuditRecord record=live.record(order,request(order,"payment_timed_out"));
        PaymentReservation reservation=reservations.create(record.getEventId());
        assertEquals(ReservationStatus.PENDING,reservation.getStatus());
        assertEquals(ReservationStatus.COMPLETED,reservations.reconnect(record.getEventId()).getStatus());

        AuditRecord recovered=records.findByEventId(record.getEventId()).orElseThrow();
        assertEquals(Outcome.RECOVERED,recovered.getOutcome());
        assertEquals(recovered.getAmount(),recovered.getRecoveredAmount());
        assertEquals(LifecycleState.RECOVERED_BY_VERIFIED_TEST_PAYMENT,recovered.getLifecycleState());
        assertNull(recovered.getStopOrEscalateReason());
        assertNull(recovered.getEscalationReason());
        java.util.List<AgentDecisionTrace> eventTraces=traces.findByEventIdOrderByIdAsc(record.getEventId());
        AgentDecisionTrace reconnectTrace=eventTraces.get(eventTraces.size()-1);
        assertEquals("Reservation completed: payment confirmed on network reconnect",reconnectTrace.getSummary());
        assertEquals("idempotency_key="+reservation.getIdempotencyKey(),reconnectTrace.getDetail());
        assertEquals(AuditActor.RESERVATION_SYSTEM,reconnectTrace.getActor());
    }

    @Test void explicitPresetReasonSurvivesSimulatedConnectionFlag() {
        RazorpayTestOrder order=order(100);
        RazorpayCheckoutEventRequest request=new RazorpayCheckoutEventRequest(order.getInternalRequestId(),
                order.getRazorpayOrderId(),null,null,RazorpayCheckoutEventService.DISMISSED,
                "insufficient_fund","customer@example.test","BAD_REQUEST_ERROR","failure","bank",
                "payment_authentication",1500L,true);

        AuditRecord record=live.record(order,request);

        assertEquals(RootCause.INSUFFICIENT_BALANCE,record.getRootCause());
        assertEquals(ActionTaken.SEND_ALT_PAYMENT_LINK,record.getActionTaken());
    }
    private RazorpayTestOrder order(int index){RazorpayTestOrder o=new RazorpayTestOrder();o.setInternalRequestId("req_live_"+index);o.setRazorpayOrderId("order_live_"+index);o.setAmountInr(new BigDecimal("50.00"));o.setAmountPaise(5000L);o.setCurrency("INR");o.setStatus("created");o.setMode("test");return o;}
    private RazorpayCheckoutEventRequest request(RazorpayTestOrder order,String reason){return new RazorpayCheckoutEventRequest(order.getInternalRequestId(),order.getRazorpayOrderId(),null,null,RazorpayCheckoutEventService.DISMISSED,reason,"customer@example.test","BAD_REQUEST_ERROR","failure","bank","payment_authentication",100L,false);}
}
