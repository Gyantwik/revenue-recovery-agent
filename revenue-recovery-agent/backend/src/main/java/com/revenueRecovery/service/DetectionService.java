package com.revenueRecovery.service;

import com.revenueRecovery.model.DetectionResult;
import com.revenueRecovery.model.Event;
import org.springframework.stereotype.Service;

@Service
public class DetectionService {

    public DetectionResult detect(Event event) {
        return new DetectionResult(true, event.getAmount());
    }
}
