package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Recurring transaction summary.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record RecurringTransaction(
        String merchantName,
        BigDecimal averageAmount,
        String currency,
        String frequency,
        int occurrences,
        SpendingCategory category,
        BigDecimal monthlyTotal
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "merchantName", merchantName,
                "averageAmount", averageAmount,
                "currency", currency,
                "frequency", frequency,
                "occurrences", occurrences,
                "category", category != null ? category.name() : null,
                "monthlyTotal", monthlyTotal
        );
    }
}
