package com.revenueRecovery.controller;

import com.revenueRecovery.controller.dto.PolicyImpactResponse;
import com.revenueRecovery.model.enums.RootCause;
import com.revenueRecovery.service.PolicySimulationService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulator")
@CrossOrigin(origins = "*") // Local development only; restrict before deployment.
public class SimulatorController {
    private final PolicySimulationService service;
    public SimulatorController(PolicySimulationService service) { this.service = service; }
    @GetMapping("/policy-impact")
    public PolicyImpactResponse impact(@RequestParam(required = false) String rootCause) {
        return service.simulate(rootCause == null || rootCause.isBlank() ? null : RootCause.fromJson(rootCause));
    }
    @GetMapping("/policy-impact/breakdown")
    public List<PolicyImpactResponse> breakdown() { return service.simulateAll(); }
}
