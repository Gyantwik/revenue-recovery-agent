package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.AuditRecordResponse;
import com.revenueRecovery.controller.dto.NextRecoveryActionResponse;
import com.revenueRecovery.controller.dto.TransactionRecoveryOrderResponse;
import com.revenueRecovery.repository.AuditRecordRepository;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.service.TransactionNextActionService;
import com.revenueRecovery.service.TransactionRecoveryCheckoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
// Local development only; lock this down to trusted frontend origins before deployment.
@CrossOrigin(origins = "*")
public class EventController {

    private final AuditRecordRepository auditRecordRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final TransactionNextActionService nextActionService;
    private final TransactionRecoveryCheckoutService recoveryCheckoutService;

    public EventController(AuditRecordRepository auditRecordRepository,
            AuditHistoryRepository auditHistoryRepository,
            TransactionNextActionService nextActionService,
            TransactionRecoveryCheckoutService recoveryCheckoutService) {
        this.auditRecordRepository = auditRecordRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.nextActionService = nextActionService;
        this.recoveryCheckoutService = recoveryCheckoutService;
    }

    @GetMapping("/{eventId}/next-action")
    public NextRecoveryActionResponse getNextAction(@PathVariable String eventId) {
        return nextActionService.decide(eventId);
    }

    @PostMapping("/{eventId}/recovery-checkout")
    public TransactionRecoveryOrderResponse createRecoveryCheckout(@PathVariable String eventId) {
        return recoveryCheckoutService.create(eventId);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<?> getTransaction(@PathVariable String eventId) {
        return auditRecordRepository.findByEventId(eventId)
                .<ResponseEntity<?>>map(record -> ResponseEntity.ok(AuditRecordResponse.from(
                        record, auditHistoryRepository.findByEventIdOrderByIdAsc(eventId))))
                .orElseGet(() -> ResponseEntity.status(404).body(notFoundBody(eventId)));
    }

    private Map<String, String> notFoundBody(String eventId) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "Transaction not found");
        error.put("eventId", eventId);
        return error;
    }
}
