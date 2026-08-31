package com.revenueRecovery.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.AuditHistoryRepository;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import com.revenueRecovery.service.RazorpayOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:razorpay-order-testdb",
        "razorpay.key-id=rzp_test_safe_unit_placeholder",
        "razorpay.key-secret=safe-unit-secret-placeholder"
})
@AutoConfigureMockMvc
@Transactional
class RazorpayTestOrderControllerTest {
    private static final String SECRET_PLACEHOLDER = "safe-unit-secret-placeholder";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RazorpayTestOrderRepository orderRepository;
    @Autowired AuditHistoryRepository historyRepository;
    @Autowired @Qualifier("razorpayRestTemplate") RestTemplate restTemplate;

    private MockRestServiceServer upstream;

    @BeforeEach
    void setUp() {
        upstream = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void validatesCurrencyAndRequestAmount() throws Exception {
        mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid order request"))
                .andExpect(jsonPath("$.message").value("Only INR currency is supported"))
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0.00,\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be between ₹1 and ₹10,000"));
        upstream.verify();
    }

    @Test
    void mockedSuccessReturnsSafeResponseAndPersistsSafeMapping() throws Exception {
        expectSuccess();

        String body = mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"INR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razorpay_order_id").value("order_safe_mock"))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("created"))
                .andExpect(jsonPath("$.mode").value("test"))
                .andReturn().getResponse().getContentAsString();

        upstream.verify();
        assertFalse(body.contains(SECRET_PLACEHOLDER));
        List<RazorpayTestOrder> stored = orderRepository.findAll();
        assertEquals(1, stored.size());
        assertEquals(50000L, stored.get(0).getAmountPaise());
        assertEquals("test", stored.get(0).getMode());
        assertTrue(stored.get(0).getReceipt().length() <= 40);
        assertTrue(Arrays.stream(RazorpayTestOrder.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("secret")));
    }

    @Test
    void mockedFourAndFiveHundredResponsesAreSafe() throws Exception {
        upstream.expect(once(), requestTo(RazorpayOrderService.ORDERS_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"" + SECRET_PLACEHOLDER + "\"}"));
        String fourHundred = performValidOrderAndExpectBadGateway();
        assertFalse(fourHundred.contains(SECRET_PLACEHOLDER));
        upstream.verify();

        upstream.reset();
        upstream.expect(once(), requestTo(RazorpayOrderService.ORDERS_URL)).andRespond(withServerError());
        String fiveHundred = performValidOrderAndExpectBadGateway();
        assertFalse(fiveHundred.contains(SECRET_PLACEHOLDER));
        upstream.verify();
    }

    @Test
    void mockedNetworkTimeoutReturnsSafeUnavailableError() throws Exception {
        upstream.expect(once(), requestTo(RazorpayOrderService.ORDERS_URL))
                .andRespond(request -> { throw new java.net.SocketTimeoutException("simulated timeout"); });

        String body = mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"INR\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Razorpay unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(SECRET_PLACEHOLDER));
        assertEquals(0, orderRepository.count());
        upstream.verify();
    }

    @Test
    void testOrderDoesNotChangeRecoveryMetricsOrHistory() throws Exception {
        mockMvc.perform(post("/api/batch/run")).andExpect(status().isOk());
        JsonNode before = objectMapper.readTree(mockMvc.perform(get("/api/batch-summary"))
                .andReturn().getResponse().getContentAsString());
        long historyBefore = historyRepository.count();

        expectSuccess();
        mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"INR\"}"))
                .andExpect(status().isOk());

        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/batch-summary"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(before, after);
        assertEquals(historyBefore, historyRepository.count());
        upstream.verify();
    }

    private void expectSuccess() {
        upstream.expect(once(), requestTo(RazorpayOrderService.ORDERS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(request -> {
                    JsonNode sent = objectMapper.readTree(
                            ((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(50000, sent.get("amount").asLong());
                    assertEquals("recoverai_test_mode", sent.get("notes").get("source").asText());
                    assertEquals("test", sent.get("notes").get("environment").asText());
                    String receipt = sent.get("receipt").asText();
                    String response = "{\"id\":\"order_safe_mock\",\"amount\":50000,"
                            + "\"currency\":\"INR\",\"receipt\":\"" + receipt
                            + "\",\"status\":\"created\"}";
                    return org.springframework.test.web.client.response.MockRestResponseCreators
                            .withSuccess(response, MediaType.APPLICATION_JSON).createResponse(request);
                });
    }

    private String performValidOrderAndExpectBadGateway() throws Exception {
        return mockMvc.perform(post("/api/razorpay/test/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.00,\"currency\":\"INR\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Razorpay upstream error"))
                .andExpect(jsonPath("$.message").value("Razorpay Test Mode order creation failed."))
                .andExpect(jsonPath("$.status").value(502))
                .andReturn().getResponse().getContentAsString();
    }
}
