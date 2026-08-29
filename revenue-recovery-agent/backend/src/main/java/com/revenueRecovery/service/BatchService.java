package com.revenueRecovery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revenueRecovery.controller.dto.SyntheticEventInput;
import com.revenueRecovery.model.AuditRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class BatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchService.class);

    private final ObjectMapper objectMapper;
    private final RevenueRecoveryPipeline pipeline;
    private final BatchSummaryService batchSummaryService;

    public BatchService(
            ObjectMapper objectMapper,
            RevenueRecoveryPipeline pipeline,
            BatchSummaryService batchSummaryService) {
        this.objectMapper = objectMapper;
        this.pipeline = pipeline;
        this.batchSummaryService = batchSummaryService;
    }

    public List<AuditRecord> runBatch() throws IOException {
        List<SyntheticEventInput> inputs;
        ClassPathResource dataset = new ClassPathResource("data/synthetic-dataset.json");
        try (InputStream inputStream = dataset.getInputStream()) {
            inputs = objectMapper.readValue(inputStream, new TypeReference<>() { });
        }

        List<AuditRecord> results = new ArrayList<>(inputs.size());
        int mismatchCount = 0;
        for (SyntheticEventInput input : inputs) {
            AuditRecord result = pipeline.processEvent(input.toEvent());
            results.add(result);
            if (result.getRootCause() != input.rootCause()) {
                mismatchCount++;
                LOGGER.warn(
                        "Classification mismatch for {}: pipeline={}, dataset_ground_truth={}",
                        input.eventId(),
                        jsonValue(result.getRootCause()),
                        jsonValue(input.rootCause()));
            }
        }
        LOGGER.info(
                "Batch run complete: total_records_processed={}, classification_mismatches={}, recovery_rate={}",
                results.size(),
                mismatchCount,
                batchSummaryService.summarize().recoveryRate());
        return results;
    }

    private String jsonValue(com.revenueRecovery.model.enums.RootCause rootCause) {
        return rootCause == null ? "null" : rootCause.toJson();
    }
}
