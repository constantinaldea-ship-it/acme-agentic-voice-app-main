package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Unusual activity indicator for spending analysis.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record UnusualActivity(
        LocalDate date,
        String merchantName,
        BigDecimal amount,
        String currency,
        String reason
) {
    public Map<String, Object> toMap() {
        return Map.of(
                "date", date != null ? date.toString() : null,
                "merchantName", merchantName,
                "amount", amount,
                "currency", currency,
                "reason", reason
        );
    }
}
