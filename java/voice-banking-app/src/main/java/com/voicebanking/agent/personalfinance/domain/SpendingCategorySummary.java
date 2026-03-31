package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Category spending summary.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record SpendingCategorySummary(
        SpendingCategory category,
        BigDecimal totalAmount,
        BigDecimal percentageOfTotal,
        int transactionCount,
        String currency
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "category", category != null ? category.name() : null,
                "totalAmount", totalAmount,
                "percentageOfTotal", percentageOfTotal,
                "transactionCount", transactionCount,
                "currency", currency
        );
    }
}
