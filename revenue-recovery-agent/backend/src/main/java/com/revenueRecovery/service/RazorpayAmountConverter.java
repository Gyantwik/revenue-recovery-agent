package com.revenueRecovery.service;

import java.math.BigDecimal;

public final class RazorpayAmountConverter {
    private static final BigDecimal MINIMUM = new BigDecimal("1.00");
    private static final BigDecimal MAXIMUM = new BigDecimal("10000.00");

    private RazorpayAmountConverter() {
    }

    public static long toPaise(BigDecimal amount) {
        if (amount == null || amount.compareTo(MINIMUM) < 0 || amount.compareTo(MAXIMUM) > 0) {
            throw new InvalidOrderRequestException("Amount must be between ₹1 and ₹10,000");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidOrderRequestException("Amount must have no more than two decimal places");
        }
        return amount.movePointRight(2).longValueExact();
    }
}
