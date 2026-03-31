package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Cash flow summary for a period.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record CashFlowSummary(
        LocalDate periodStart,
        LocalDate periodEnd,
        Map<String, BigDecimal> incomeByCurrency,
        Map<String, BigDecimal> expensesByCurrency,
        Map<String, BigDecimal> netByCurrency
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "periodStart", periodStart != null ? periodStart.toString() : null,
                "periodEnd", periodEnd != null ? periodEnd.toString() : null,
                "incomeByCurrency", incomeByCurrency,
                "expensesByCurrency", expensesByCurrency,
                "netByCurrency", netByCurrency
        );
    }
}
