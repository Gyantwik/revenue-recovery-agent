package com.revenueRecovery.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RazorpayTestConfigResponse(@JsonProperty("key_id") String keyId, String mode) {
}
