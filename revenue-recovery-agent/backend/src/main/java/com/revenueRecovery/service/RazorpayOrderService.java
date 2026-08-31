package com.revenueRecovery.service;

import com.revenueRecovery.config.RazorpayProperties;
import com.revenueRecovery.controller.dto.CreateRazorpayTestOrderRequest;
import com.revenueRecovery.controller.dto.RazorpayTestOrderResponse;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RazorpayOrderService {
    public static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final RazorpayProperties properties;
    private final RestTemplate restTemplate;
    private final RazorpayTestOrderRepository repository;

    public RazorpayOrderService(RazorpayProperties properties,
            @Qualifier("razorpayRestTemplate") RestTemplate restTemplate,
            RazorpayTestOrderRepository repository) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.repository = repository;
    }

    @Transactional
    public RazorpayTestOrderResponse createTestOrder(CreateRazorpayTestOrderRequest request) {
        validateConfiguration();
        if (request == null) throw new InvalidOrderRequestException("Request body is required");
        if (!"INR".equals(request.currency())) {
            throw new InvalidOrderRequestException("Only INR currency is supported");
        }

        long amountPaise = RazorpayAmountConverter.toPaise(request.amount());
        BigDecimal amountInr = request.amount().setScale(2);
        String internalRequestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        String receipt = "recoverai_" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret(), StandardCharsets.UTF_8);
        UpstreamOrderRequest upstreamRequest = new UpstreamOrderRequest(amountPaise, "INR", receipt,
                Map.of("source", "recoverai_test_mode", "environment", "test"));

        UpstreamOrderResponse upstream;
        try {
            ResponseEntity<UpstreamOrderResponse> response = restTemplate.postForEntity(
                    ORDERS_URL, new HttpEntity<>(upstreamRequest, headers), UpstreamOrderResponse.class);
            upstream = response.getBody();
        } catch (HttpStatusCodeException exception) {
            throw RazorpayServiceException.upstreamFailure();
        } catch (ResourceAccessException exception) {
            throw RazorpayServiceException.temporarilyUnavailable();
        } catch (RestClientException exception) {
            throw RazorpayServiceException.upstreamFailure();
        }

        if (!isValidCreatedOrder(upstream, amountPaise, receipt)) {
            throw RazorpayServiceException.upstreamFailure();
        }

        RazorpayTestOrder stored = new RazorpayTestOrder();
        stored.setInternalRequestId(internalRequestId);
        stored.setRazorpayOrderId(upstream.id());
        stored.setReceipt(receipt);
        stored.setAmountInr(amountInr);
        stored.setAmountPaise(amountPaise);
        stored.setCurrency("INR");
        stored.setStatus("created");
        stored.setMode("test");
        stored.setCreatedAt(Instant.now());
        repository.save(stored);

        return new RazorpayTestOrderResponse(internalRequestId, upstream.id(), amountPaise,
                "INR", receipt, "created", "test");
    }

    private void validateConfiguration() {
        String keyId = properties.getKeyId();
        String secret = properties.getKeySecret();
        if (keyId == null || secret == null || keyId.isBlank() || secret.isBlank()
                || !keyId.startsWith("rzp_test_") || keyId.startsWith("rzp_live_")) {
            throw RazorpayServiceException.notConfigured();
        }
    }

    private boolean isValidCreatedOrder(UpstreamOrderResponse response, long amountPaise, String receipt) {
        return response != null
                && response.id() != null && response.id().startsWith("order_")
                && response.amount() == amountPaise
                && "INR".equals(response.currency())
                && receipt.equals(response.receipt())
                && "created".equals(response.status());
    }

    record UpstreamOrderRequest(long amount, String currency, String receipt, Map<String, String> notes) {
    }

    record UpstreamOrderResponse(String id, long amount, String currency, String receipt, String status) {
    }
}
