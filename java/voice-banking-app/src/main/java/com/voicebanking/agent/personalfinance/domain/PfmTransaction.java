package com.voicebanking.agent.personalfinance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unified personal finance transaction view.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public record PfmTransaction(
        String source,
        String referenceId,
        LocalDate date,
        String description,
        String merchantName,
        BigDecimal amount,
        String currency,
        SpendingCategory category
) {
    public boolean isIncome() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isSpending() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public BigDecimal spendingAmount() {
        return isSpending() ? amount.abs() : BigDecimal.ZERO;
    }
}
