package com.revenueRecovery.service;

import com.revenueRecovery.model.AuditRecord;
import com.revenueRecovery.model.ClassificationResult;
import com.revenueRecovery.model.DetectionResult;
import com.revenueRecovery.model.Event;
import com.revenueRecovery.model.PolicyDecision;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class RevenueRecoveryPipeline {

    private static final double CONFIDENCE_THRESHOLD = 0.75;

    private final EventRepository eventRepository;
    private final DetectionService detectionService;
    private final ClassificationService classificationService;
    private final PolicyEngine policyEngine;
    private final ActionExecutor actionExecutor;
    private final AuditService auditService;

    public RevenueRecoveryPipeline(
            EventRepository eventRepository,
            DetectionService detectionService,
            ClassificationService classificationService,
            PolicyEngine policyEngine,
            ActionExecutor actionExecutor,
            AuditService auditService) {
        this.eventRepository = eventRepository;
        this.detectionService = detectionService;
        this.classificationService = classificationService;
        this.policyEngine = policyEngine;
        this.actionExecutor = actionExecutor;
        this.auditService = auditService;
    }

    @Transactional
    public AuditRecord processEvent(Event event) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(event.getEventId(), "eventId must not be null");
        Event normalizedEvent = eventRepository.findByEventId(event.getEventId())
                .orElseGet(() -> eventRepository.save(event));

        DetectionResult detection = detectionService.detect(normalizedEvent);
        ClassificationResult classification = classificationService.classify(normalizedEvent);

        RootCause effectiveRootCause = classification.getConfidence() < CONFIDENCE_THRESHOLD
                ? RootCause.UNKNOWN
                : classification.getRootCause();
        PolicyDecision policy = policyEngine.decide(effectiveRootCause);

        AuditRecord execution = actionExecutor.execute(normalizedEvent, policy);
        if (execution.getId() != null) {
            return auditService.trackOutcome(execution);
        }

        AuditRecord savedAudit = auditService.logDecision(
                normalizedEvent, detection, classification, policy, execution);
        return auditService.trackOutcome(savedAudit);
    }
}
