package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Spending breakdown for a period.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record SpendingBreakdown(
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, BigDecimal> totalsByCurrency,
        List<SpendingCategorySummary> categories
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "periodStart", periodStart != null ? periodStart.toString() : null,
                "periodEnd", periodEnd != null ? periodEnd.toString() : null,
                "totalsByCurrency", totalsByCurrency,
                "categories", categories.stream().map(SpendingCategorySummary::toMap).toList()
        );
    }
}
