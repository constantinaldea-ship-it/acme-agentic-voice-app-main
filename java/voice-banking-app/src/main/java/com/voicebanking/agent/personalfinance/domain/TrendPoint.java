package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * Single trend point for a month.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record TrendPoint(
        YearMonth month,
        BigDecimal totalSpending,
        BigDecimal changeAmount,
        BigDecimal changePercent,
        String currency
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "month", month != null ? month.toString() : null,
                "totalSpending", totalSpending,
                "changeAmount", changeAmount,
                "changePercent", changePercent,
                "currency", currency
        );
    }
}
