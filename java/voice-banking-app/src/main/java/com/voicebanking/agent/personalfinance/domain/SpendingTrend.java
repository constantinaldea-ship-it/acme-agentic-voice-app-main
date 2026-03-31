package com.voicebanking.agent.personalfinance.domain;

import java.util.List;
import java.util.Map;

/**
 * Spending trend over multiple months.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record SpendingTrend(List<TrendPoint> points) {
    public Map<String, Object> toMap() {
        return Map.of(
                "months", points.size(),
                "points", points.stream().map(TrendPoint::toMap).toList()
        );
    }
}
