package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Budget definition.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record Budget(
        SpendingCategory category,
        BigDecimal amount,
        String currency,
        BudgetPeriod period,
        LocalDate startDate
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "category", category != null ? category.name() : null,
                "amount", amount,
                "currency", currency,
                "period", period != null ? period.name() : null,
                "startDate", startDate != null ? startDate.toString() : null
        );
    }
}
