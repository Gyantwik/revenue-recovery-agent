package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.AuditRecordResponse;
import com.revenueRecovery.repository.AuditRecordRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public EventController(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<?> getTransaction(@PathVariable String eventId) {
        return auditRecordRepository.findByEventId(eventId)
                .<ResponseEntity<?>>map(record -> ResponseEntity.ok(AuditRecordResponse.from(record)))
                .orElseGet(() -> ResponseEntity.status(404).body(notFoundBody(eventId)));
    }

    private Map<String, String> notFoundBody(String eventId) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "Transaction not found");
        error.put("eventId", eventId);
        return error;
    }
}
