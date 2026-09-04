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
    void all80SeededEventsMatchTheDynamicEligibilityMatrix() {
        List<AuditRecord> records = auditRepository.findAll();
        assertEquals(80, records.size());

        for (AuditRecord record : records) {
            var decision = eligibilityService.evaluate(record);
            boolean expected = expectedAllowed(record);
            assertEquals(expected, decision.allowed(), record.getEventId());
            assertEquals(expected ? expectedActionType(record) : NextActionType.NONE,
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

        assertEquals(80, auditRepository.count());
        assertEquals(80, eventRepository.count());
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
            case CHECKOUT_ABANDONED, MANDATE_EXPIRED, PAYMENT_PENDING -> true;
            case BANK_TEMP_ERROR -> record.getAttemptNumber() != null
                    && record.getAttemptNumber() < record.getMaxAttemptsAllowed()
                    && record.getLifecycleState() != com.revenueRecovery.model.enums.LifecycleState.RETRY_SCHEDULED;
            case MANDATE_FAILED_RETRYABLE ->
                    record.getMaxAttemptsAllowed() != null && record.getMaxAttemptsAllowed() > 0
                            && record.getAttemptNumber() != null
                            && record.getAttemptNumber() >= record.getMaxAttemptsAllowed();
            default -> false;
        };
    }

    private NextActionType expectedActionType(AuditRecord record) {
        return record.getRootCause() == RootCause.PAYMENT_PENDING
                ? NextActionType.DISPLAY_INFORMATION : NextActionType.OPEN_RECOVERY_CHECKOUT;
    }
}
