package com.revenueRecovery.service;

import com.revenueRecovery.controller.dto.BatchSummaryResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchSummaryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AuditRecordRepository auditRecordRepository;

    public BatchSummaryService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    public BatchSummaryResponse summarize() {
        List<AuditRecord> records = auditRecordRepository.findAll();
        BigDecimal totalAtRisk = sum(records, true);
        BigDecimal totalRecovered = sum(records, false);
        BigDecimal recoveryRate = totalAtRisk.signum() == 0
                ? ZERO.setScale(4)
                : totalRecovered.divide(totalAtRisk, 4, RoundingMode.HALF_UP);

        Map<RootCause, CauseAccumulator> byCause = new EnumMap<>(RootCause.class);
        List<BatchSummaryResponse.EscalatedSummary> escalated = new ArrayList<>();
        for (AuditRecord record : records) {
            byCause.computeIfAbsent(record.getRootCause(), ignored -> new CauseAccumulator())
                    .add(record);
            if (record.getOutcome() == Outcome.ESCALATED) {
                escalated.add(new BatchSummaryResponse.EscalatedSummary(
                        record.getEventId(),
                        record.getRootCause(),
                        record.getAmount(),
                        record.getStopOrEscalateReason()));
            }
        }

        List<BatchSummaryResponse.CauseSummary> causeSummaries = byCause.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey()))
                .toList();
        return new BatchSummaryResponse(
                totalAtRisk,
                totalRecovered,
                recoveryRate,
                records.size(),
                causeSummaries,
                escalated);
    }

    private BigDecimal sum(List<AuditRecord> records, boolean risk) {
        return records.stream()
                .map(record -> risk ? record.getRiskAmount() : record.getRecoveredAmount())
                .filter(java.util.Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
    }

    private static final class CauseAccumulator {
        private int count;
        private BigDecimal totalAmount = ZERO;
        private BigDecimal recoveredAmount = ZERO;

        private void add(AuditRecord record) {
            count++;
            totalAmount = totalAmount.add(valueOrZero(record.getAmount()));
            recoveredAmount = recoveredAmount.add(valueOrZero(record.getRecoveredAmount()));
        }

        private BatchSummaryResponse.CauseSummary toResponse(RootCause rootCause) {
            return new BatchSummaryResponse.CauseSummary(
                    rootCause, count, totalAmount, recoveredAmount);
        }

        private static BigDecimal valueOrZero(BigDecimal value) {
            return value == null ? ZERO : value;
        }
    }
}
