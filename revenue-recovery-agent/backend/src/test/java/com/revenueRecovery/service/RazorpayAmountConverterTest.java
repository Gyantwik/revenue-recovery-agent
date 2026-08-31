package com.revenueRecovery.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RazorpayAmountConverterTest {
    @Test
    void convertsFiveHundredRupeesToPaise() {
        assertEquals(50000L, RazorpayAmountConverter.toPaise(new BigDecimal("500.00")));
    }

    @Test
    void acceptsMinimumAndMaximum() {
        assertEquals(100L, RazorpayAmountConverter.toPaise(new BigDecimal("1.00")));
        assertEquals(1_000_000L, RazorpayAmountConverter.toPaise(new BigDecimal("10000.00")));
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThrows(InvalidOrderRequestException.class,
                () -> RazorpayAmountConverter.toPaise(BigDecimal.ZERO));
        assertThrows(InvalidOrderRequestException.class,
                () -> RazorpayAmountConverter.toPaise(new BigDecimal("-1.00")));
    }

    @Test
    void rejectsAmountsAboveDemoMaximum() {
        assertThrows(InvalidOrderRequestException.class,
                () -> RazorpayAmountConverter.toPaise(new BigDecimal("10000.01")));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThrows(InvalidOrderRequestException.class,
                () -> RazorpayAmountConverter.toPaise(new BigDecimal("1.001")));
    }
}
