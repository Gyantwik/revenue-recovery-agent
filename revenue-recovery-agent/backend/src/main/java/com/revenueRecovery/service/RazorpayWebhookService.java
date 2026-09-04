package com.revenueRecovery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revenueRecovery.config.RazorpayProperties;
import com.revenueRecovery.controller.dto.RazorpayCheckoutEventRequest;
import com.revenueRecovery.model.RazorpayTestOrder;
import com.revenueRecovery.repository.RazorpayTestOrderRepository;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class RazorpayWebhookService {
    private final RazorpayProperties properties;
    private final ObjectMapper mapper;
    private final RazorpayTestOrderRepository orders;
    private final LiveTransactionService liveTransactions;

    public RazorpayWebhookService(RazorpayProperties properties, ObjectMapper mapper,
            RazorpayTestOrderRepository orders, LiveTransactionService liveTransactions) {
        this.properties = properties; this.mapper = mapper; this.orders = orders;
        this.liveTransactions = liveTransactions;
    }

    public WebhookResult handle(String payload, String signature) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            return new WebhookResult(503, "webhook_not_configured");
        }
        if (!valid(payload, signature, properties.getWebhookSecret())) {
            return new WebhookResult(401, "invalid_signature");
        }
        try {
            JsonNode root = mapper.readTree(payload);
            String eventType = root.path("event").asText();
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String orderId = text(payment, "order_id");
            RazorpayTestOrder order = orders.findByRazorpayOrderId(orderId).orElse(null);
            if (order == null) return new WebhookResult(202, "ignored_unknown_order");

            boolean success = "payment.captured".equals(eventType) || "payment.authorized".equals(eventType);
            if (!success && !"payment.failed".equals(eventType)) return new WebhookResult(202, "ignored_event");
            String customer = first(text(payment, "email"), text(payment, "contact"), "Checkout customer");
            RazorpayCheckoutEventRequest request = new RazorpayCheckoutEventRequest(
                    order.getInternalRequestId(), orderId, text(payment, "id"), null,
                    success ? RazorpayCheckoutEventService.SUCCESS : RazorpayCheckoutEventService.DISMISSED,
                    text(payment, "error_reason"), customer, text(payment, "error_code"),
                    text(payment, "error_description"), text(payment, "error_source"),
                    text(payment, "error_step"), null, false);
            liveTransactions.record(order, request);
            if (success) liveTransactions.markVerified(orderId, text(payment, "id"));
            return new WebhookResult(200, success ? "payment_recorded" : "failure_recorded");
        } catch (Exception exception) {
            return new WebhookResult(400, "invalid_payload");
        }
    }

    private boolean valid(String payload, String signature, String secret) {
        if (payload == null || signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (Exception exception) { return false; }
    }
    private String text(JsonNode node, String name) {
        String value = node.path(name).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
    private String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "Checkout customer";
    }
    public record WebhookResult(int status, String result) {}
}
