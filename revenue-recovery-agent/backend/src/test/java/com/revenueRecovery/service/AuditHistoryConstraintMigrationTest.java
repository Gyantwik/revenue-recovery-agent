package com.revenueRecovery.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-constraint-migration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditHistoryConstraintMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void verifiedRecoveryActorFailsWithLegacyConstraintAndPersistsAfterSchemaMigration() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc.execute("ALTER TABLE audit_history DROP CONSTRAINT ck_audit_history_actor");
        jdbc.execute("ALTER TABLE audit_history ADD CONSTRAINT CONSTRAINT_E8 "
                + "CHECK (actor IN ('SYSTEM_SIMULATION','MERCHANT_MANUAL'))");

        assertThrows(DataIntegrityViolationException.class, () -> insertVerifiedRecovery("legacy-rejected"));

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        assertDoesNotThrow(() -> insertVerifiedRecovery("migration-accepted"));
        jdbc.update("DELETE FROM audit_history WHERE event_id = 'TXN-CONSTRAINT-REGRESSION'");
    }

    private void insertVerifiedRecovery(String suffix) {
        jdbc.update("""
                INSERT INTO audit_history
                    (event_id, timestamp, previous_state, new_state, outcome_if_terminal,
                     actor, idempotency_key)
                VALUES (?, CURRENT_TIMESTAMP, 'NOT_RECOVERED',
                        'RECOVERED_BY_VERIFIED_TEST_PAYMENT', 'RECOVERED',
                        'RAZORPAY_TEST_VERIFICATION', ?)
                """, "TXN-CONSTRAINT-REGRESSION", "constraint-regression:" + suffix);
    }
}
