package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Budget status with actual spend.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record BudgetStatus(
        Budget budget,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentUsed,
        boolean onTrack,
        long daysRemaining
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "budget", budget != null ? budget.toMap() : null,
                "spent", spent,
                "remaining", remaining,
                "percentUsed", percentUsed,
                "onTrack", onTrack,
                "daysRemaining", daysRemaining
        );
    }
}
