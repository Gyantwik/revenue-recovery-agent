package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.NextActionType;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:phase6d-seed-eligibility;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class SyntheticDatasetRecoveryEligibilityTest {
    @Autowired AuditRecordRepository auditRepository;
    @Autowired EventRepository eventRepository;
    @Autowired RecoveryTransactionEligibilityService eligibilityService;
    @Autowired SyntheticDatasetSeeder seeder;

    @Test
    void all65SeededEventsMatchTheDynamicEligibilityMatrix() {
        List<AuditRecord> records = auditRepository.findAll();
        assertEquals(65, records.size());

        for (AuditRecord record : records) {
            var decision = eligibilityService.evaluate(record);
            boolean expected = expectedAllowed(record);
            assertEquals(expected, decision.allowed(), record.getEventId());
            assertEquals(expected ? NextActionType.OPEN_RECOVERY_CHECKOUT : NextActionType.NONE,
                    decision.actionType(), record.getEventId());
            if (record.getOutcome() == Outcome.RECOVERED) {
                assertEquals("Already Recovered", decision.buttonLabel(), record.getEventId());
            } else if (record.getOutcome() == Outcome.ESCALATED) {
                assertEquals("Escalated for Review", decision.buttonLabel(), record.getEventId());
            }
        }
    }

    @Test
    void startupSeederNeverResetsExistingLiveState() throws Exception {
        AuditRecord record = auditRepository.findByEventId("TXN10044").orElseThrow();
        record.setOutcome(Outcome.RECOVERED);
        record.setRecoveredAmount(record.getAmount());
        auditRepository.saveAndFlush(record);

        seeder.run(null);
        seeder.run(null);

        assertEquals(65, auditRepository.count());
        assertEquals(65, eventRepository.count());
        AuditRecord retained = auditRepository.findByEventId("TXN10044").orElseThrow();
        assertEquals(Outcome.RECOVERED, retained.getOutcome());
        assertEquals(0, retained.getAmount().compareTo(retained.getRecoveredAmount()));
    }

    private boolean expectedAllowed(AuditRecord record) {
        if (record.getOutcome() == Outcome.RECOVERED || record.getOutcome() == Outcome.ESCALATED) {
            return false;
        }
        RootCause cause = record.getRootCause();
        return switch (cause) {
            case CHECKOUT_ABANDONED, INSUFFICIENT_BALANCE, INCORRECT_PIN, MANDATE_EXPIRED -> true;
            case BANK_TEMP_ERROR, MANDATE_FAILED_RETRYABLE ->
                    record.getMaxAttemptsAllowed() != null && record.getMaxAttemptsAllowed() > 0
                            && record.getAttemptNumber() != null
                            && record.getAttemptNumber() >= record.getMaxAttemptsAllowed();
            default -> false;
        };
    }
}
