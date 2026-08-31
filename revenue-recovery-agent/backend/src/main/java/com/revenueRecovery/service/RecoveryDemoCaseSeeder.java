package com.revenueRecovery.service;

import com.revenueRecovery.model.RecoveryDemoCase;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.RecoveryDemoCaseRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class RecoveryDemoCaseSeeder implements ApplicationRunner {
    public static final String DEMO_EVENT_ID = "TXN_DEMO_RECOVERY_001";
    public static final String AWAITING_PAYMENT = "awaiting_customer_payment";

    private final RecoveryDemoCaseRepository repository;

    public RecoveryDemoCaseSeeder(RecoveryDemoCaseRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.findByEventId(DEMO_EVENT_ID).isPresent()) return;
        RecoveryDemoCase demo = new RecoveryDemoCase();
        demo.setEventId(DEMO_EVENT_ID);
        demo.setCaseType("payment_degradation");
        demo.setRootCause(RootCause.CHECKOUT_ABANDONED);
        demo.setAmount(new BigDecimal("500.00"));
        demo.setCurrency("INR");
        demo.setPolicyAction(ActionTaken.SEND_RECOVERY_LINK);
        demo.setOutcome(Outcome.NOT_RECOVERED);
        demo.setRecoveryStatus(AWAITING_PAYMENT);
        demo.setDemoOnly(true);
        demo.setCreatedAt(Instant.parse("2026-08-31T00:00:00Z"));
        repository.save(demo);
    }
}
