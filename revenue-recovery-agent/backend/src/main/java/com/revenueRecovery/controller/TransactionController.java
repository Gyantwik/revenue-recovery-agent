package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.AuditRecordResponse;
import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.enums.Outcome;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
// Local development only; lock this down to trusted frontend origins before deployment.
@CrossOrigin(origins = "*")
public class TransactionController {

    private final AuditRecordRepository auditRecordRepository;

    public TransactionController(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    @GetMapping
    public List<AuditRecordResponse> getTransactions(
            @RequestParam(required = false) String cause,
            @RequestParam(required = false) String status) {
        RootCause rootCause = cause == null || cause.isBlank() ? null : RootCause.fromJson(cause);
        Outcome outcome = status == null || status.isBlank() ? null : Outcome.fromJson(status);

        return auditRecordRepository.findAll().stream()
                .filter(record -> rootCause == null || record.getRootCause() == rootCause)
                .filter(record -> outcome == null || record.getOutcome() == outcome)
                .map(AuditRecordResponse::from)
                .toList();
    }
}
