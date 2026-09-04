package com.revenueRecovery.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiClient.class);
    private static final String API_ROOT =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    GeminiClient(GeminiProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isConfigured() {
        String apiKey = properties.getApiKey();
        return apiKey != null
                && !apiKey.isBlank()
                && !apiKey.toLowerCase().contains("replace_with");
    }

    public String generate(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            LOGGER.debug("Gemini API key is not configured; using local fallback");
            return null;
        }

        try {
            String model = requireModel();
            String endpoint = API_ROOT
                    + encodePathSegment(model)
                    + ":generateContent?key="
                    + URLEncoder.encode(properties.getApiKey().trim(), StandardCharsets.UTF_8);

            Map<String, Object> payload = Map.of(
                    "contents", List.of(Map.of(
                            "role", "user",
                            "parts", List.of(Map.of(
                                    "text", systemPrompt + "\n\n" + userPrompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "responseMimeType", "application/json"));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Gemini request failed with HTTP status {}; using local fallback",
                        response.statusCode());
                return null;
            }
            if (response.body() == null || response.body().length() > MAX_RESPONSE_BYTES) {
                LOGGER.warn("Gemini returned an empty or oversized response; using local fallback");
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual() || text.asText().isBlank()) {
                LOGGER.warn("Gemini response did not contain candidate text; using local fallback");
                return null;
            }
            return text.asText();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Gemini request was interrupted; using local fallback");
            return null;
        } catch (Exception exception) {
            // Never log prompts, response bodies, or URLs because they may contain credentials or PII.
            LOGGER.warn("Gemini request failed ({}); using local fallback",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private String requireModel() {
        String model = properties.getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("Gemini model is not configured");
        }
        return model.trim();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
