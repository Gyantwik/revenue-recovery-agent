package com.revenueRecovery.service;

import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SyntheticDatasetSeeder implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyntheticDatasetSeeder.class);
    private final AuditRecordRepository auditRepository;
    private final EventRepository eventRepository;
    private final BatchService batchService;

    public SyntheticDatasetSeeder(AuditRecordRepository auditRepository,
            EventRepository eventRepository, BatchService batchService) {
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
        this.batchService = batchService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long auditCount = auditRepository.count();
        long eventCount = eventRepository.count();
        if (auditCount == 0 && eventCount == 0) {
            LOGGER.info("Empty database detected; seeding the canonical synthetic dataset once");
            batchService.runBatch();
            return;
        }
        LOGGER.info("Existing recovery data detected; seed skipped (events={}, auditRecords={})",
                eventCount, auditCount);
    }
}
