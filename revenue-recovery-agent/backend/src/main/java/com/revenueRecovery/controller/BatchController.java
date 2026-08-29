package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.BatchSummaryResponse;
import com.revenueRecovery.service.BatchService;
import com.revenueRecovery.service.BatchSummaryService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
@RestController
@RequestMapping("/api")
// Local development only; lock this down to trusted frontend origins before deployment.
@CrossOrigin(origins = "*")
public class BatchController {

    private final BatchService batchService;
    private final BatchSummaryService batchSummaryService;

    public BatchController(BatchService batchService, BatchSummaryService batchSummaryService) {
        this.batchService = batchService;
        this.batchSummaryService = batchSummaryService;
    }

    @PostMapping("/batch/run")
    public BatchSummaryResponse runBatch() throws IOException {
        batchService.runBatch();
        return batchSummaryService.summarize();
    }

    @GetMapping("/batch-summary")
    public BatchSummaryResponse getBatchSummary() {
        return batchSummaryService.summarize();
    }
}
