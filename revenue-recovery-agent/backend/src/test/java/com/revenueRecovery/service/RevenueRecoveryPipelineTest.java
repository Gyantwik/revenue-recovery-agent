package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pipeline-testdb")
@Transactional
class RevenueRecoveryPipelineTest {

    @Autowired
    private RevenueRecoveryPipeline pipeline;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Test
    void stopsCorrectlyWhenTheUserCancelled() {
        Event event = new Event();
        event.setEventId("cancelled-" + UUID.randomUUID());
        event.setCaseType("payment_degradation");
        event.setAmount(new BigDecimal("125.00"));
        event.setCurrency("INR");
        event.setTimestamp(Instant.parse("2026-08-29T09:30:00Z"));
        event.setSignalsUsed(List.of("gateway_response_code: CANCELLED_BY_USER"));

        AuditRecord result = pipeline.processEvent(event);

        assertEquals(Outcome.STOPPED_CORRECTLY, result.getOutcome());
        assertEquals(ActionTaken.NO_ACTION_STOP, result.getActionTaken());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getRecoveredAmount()));
    }

    @Test
    void testConfidenceOverrideEscalates() {
        Event event = eventWithSignal(
                "unrecognized-" + UUID.randomUUID(),
                "totally_unrecognized_code_xyz");

        AuditRecord result = pipeline.processEvent(event);

        assertEquals(Outcome.ESCALATED, result.getOutcome());
        assertEquals(ActionTaken.ESCALATE_MERCHANT, result.getActionTaken());
    }

    @Test
    void testIdempotentReprocessing() {
        String eventId = "idempotent-" + UUID.randomUUID();
        Event event = eventWithSignal(eventId, "BANK_TIMEOUT");

        AuditRecord firstResult = pipeline.processEvent(event);
        AuditRecord secondResult = pipeline.processEvent(event);

        assertEquals(firstResult.getId(), secondResult.getId());
        long recordsForEvent = auditRecordRepository.findAll().stream()
                .filter(record -> eventId.equals(record.getEventId()))
                .count();
        assertEquals(1L, recordsForEvent);
    }

    @Test
    void testAttemptNumberNeverExceedsMax() {
        boolean recoveredOnAttemptOne = false;
        boolean recoveredOnAttemptTwo = false;

        for (int index = 0; index < 20; index++) {
            Event event = eventWithSignal(
                    "mandate-" + UUID.randomUUID(),
                    "DECLINED_BY_BANK");

            AuditRecord result = pipeline.processEvent(event);

            assertTrue(result.getAttemptNumber() >= 0, event.getEventId());
            assertTrue(result.getAttemptNumber() <= 2, event.getEventId());
            if (result.getOutcome() == Outcome.RECOVERED && result.getAttemptNumber() == 1) {
                recoveredOnAttemptOne = true;
            }
            if (result.getOutcome() == Outcome.RECOVERED && result.getAttemptNumber() == 2) {
                recoveredOnAttemptTwo = true;
            }
        }

        assertTrue(recoveredOnAttemptOne, "Expected at least one recovery on attempt 1");
        assertTrue(recoveredOnAttemptTwo, "Expected at least one recovery on attempt 2");
    }

    private Event eventWithSignal(String eventId, String signal) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setCaseType("payment_degradation");
        event.setAmount(new BigDecimal("125.00"));
        event.setCurrency("INR");
        event.setTimestamp(Instant.parse("2026-08-29T09:30:00Z"));
        event.setSignalsUsed(List.of(signal));
        return event;
    }
}
