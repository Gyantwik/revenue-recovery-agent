package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.ActionResultResponse;
import com.revenueRecovery.model.enums.ActionTaken;
import com.revenueRecovery.service.RecoveryActionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions/{eventId}/actions")
@CrossOrigin(origins = "*") // Local development only; restrict before deployment.
public class RecoveryActionController {
    private final RecoveryActionService service;
    public RecoveryActionController(RecoveryActionService service) { this.service = service; }

    @PostMapping("/retry")
    public ActionResultResponse retry(@PathVariable String eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.triggerRetry(eventId, key);
    }
    @PostMapping("/verify-status")
    public ActionResultResponse verify(@PathVariable String eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.trigger(eventId, ActionTaken.VERIFY_STATUS, key);
    }
    @PostMapping("/send-recovery-link")
    public ActionResultResponse recoveryLink(@PathVariable String eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.trigger(eventId, ActionTaken.SEND_RECOVERY_LINK, key);
    }
    @PostMapping("/send-alt-payment-link")
    public ActionResultResponse alternateLink(@PathVariable String eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.trigger(eventId, ActionTaken.SEND_ALT_PAYMENT_LINK, key);
    }
    @PostMapping("/escalate")
    public ActionResultResponse escalate(@PathVariable String eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.trigger(eventId, ActionTaken.ESCALATE_MERCHANT, key);
    }
    @PostMapping("/stop")
    public ActionResultResponse stop(@PathVariable String eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.trigger(eventId, ActionTaken.NO_ACTION_STOP, key);
    }
}
