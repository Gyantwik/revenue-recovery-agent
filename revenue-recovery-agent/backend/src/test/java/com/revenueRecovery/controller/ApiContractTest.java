package com.revenueRecovery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api-contract-testdb")
@AutoConfigureMockMvc
@Transactional
class ApiContractTest {

    private static final Set<String> AUDIT_FIELDS = Set.of(
            "event_id", "case_type", "amount", "currency", "timestamp", "is_at_risk",
            "risk_amount", "root_cause", "classification_confidence", "signals_used",
            "policy_rule_matched", "action_taken", "attempt_number", "max_attempts_allowed",
            "outcome", "recovered_amount", "stop_or_escalate_reason");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesTheRequiredApiContract() throws Exception {
        mockMvc.perform(post("/api/batch/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(65))
                .andExpect(jsonPath("$.by_cause").isArray())
                .andExpect(jsonPath("$.escalated_summary").isArray());

        String transactionsJson = mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(65))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode firstRecord = objectMapper.readTree(transactionsJson).get(0);
        Set<String> actualFields = new TreeSet<>();
        Iterator<String> fieldNames = firstRecord.fieldNames();
        fieldNames.forEachRemaining(actualFields::add);
        assertEquals(new TreeSet<>(AUDIT_FIELDS), actualFields);

        mockMvc.perform(get("/api/batch-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(65))
                .andExpect(jsonPath("$.by_cause").isArray())
                .andExpect(jsonPath("$.escalated_summary").isArray());

        mockMvc.perform(get("/api/transactions").param("cause", "user_cancelled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].root_cause").value("user_cancelled"));

        mockMvc.perform(get("/api/transactions/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found"))
                .andExpect(jsonPath("$.eventId").value("does-not-exist"));
    }

    @Test
    void runningBatchTwiceDoesNotCreateDuplicates() throws Exception {
        int firstTotalCases = objectMapper.readTree(mockMvc.perform(post("/api/batch/run"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("total_cases")
                .asInt();

        int secondTotalCases = objectMapper.readTree(mockMvc.perform(post("/api/batch/run"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("total_cases")
                .asInt();

        assertEquals(firstTotalCases, secondTotalCases);
        assertEquals(65, secondTotalCases);
    }
}
