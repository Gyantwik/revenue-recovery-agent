package com.revenueRecovery.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:razorpay-missing-config-testdb",
        "razorpay.key-id=",
        "razorpay.key-secret="
})
@AutoConfigureMockMvc
class RazorpayMissingConfigurationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void missingConfigurationReturnsSafe503WhileExistingEndpointsStillWork() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"INR\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Razorpay unavailable"))
                .andExpect(jsonPath("$.message").value("Razorpay Test Mode is not configured."))
                .andExpect(jsonPath("$.status").value(503));

        mockMvc.perform(get("/api/batch-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_cases").value(0));
    }
}
