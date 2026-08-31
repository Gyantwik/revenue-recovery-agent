package com.revenueRecovery.controller.dto;

import java.math.BigDecimal;

public record CreateRazorpayTestOrderRequest(BigDecimal amount, String currency) {
}
