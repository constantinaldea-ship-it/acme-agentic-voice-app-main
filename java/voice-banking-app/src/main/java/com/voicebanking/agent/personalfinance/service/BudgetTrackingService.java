package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.personalfinance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Budget tracking service (mock budgets for PoC).
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class BudgetTrackingService {
    private static final Logger log = LoggerFactory.getLogger(BudgetTrackingService.class);

    public List<BudgetStatus> getBudgetStatus(List<PfmTransaction> transactions, YearMonth month) {
        List<Budget> budgets = loadMockBudgets();
        List<BudgetStatus> statuses = new ArrayList<>();

        LocalDate periodStart = month.atDay(1);
        LocalDate periodEnd = month.atEndOfMonth();
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), periodEnd);
        long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        double elapsedRatio = Math.min(1.0, Math.max(0, (double) (totalDays - daysRemaining) / totalDays));

        for (Budget budget : budgets) {
            BigDecimal spent = transactions.stream()
                    .filter(PfmTransaction::isSpending)
                    .filter(t -> t.category() == budget.category())
                    .filter(t -> budget.currency().equalsIgnoreCase(t.currency()))
                    .filter(t -> !t.date().isBefore(periodStart) && !t.date().isAfter(periodEnd))
                    .map(PfmTransaction::spendingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal remaining = budget.amount().subtract(spent).max(BigDecimal.ZERO);
            BigDecimal percent = budget.amount().compareTo(BigDecimal.ZERO) > 0
                    ? spent.multiply(BigDecimal.valueOf(100)).divide(budget.amount(), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            boolean onTrack = spent.compareTo(budget.amount().multiply(BigDecimal.valueOf(elapsedRatio + 0.05))) <= 0;

            statuses.add(new BudgetStatus(
                    budget,
                    spent,
                    remaining,
                    percent,
                    onTrack,
                    Math.max(daysRemaining, 0)
            ));
        }

        log.info("Calculated budget status for {} budgets", statuses.size());
        return statuses;
    }

    private List<Budget> loadMockBudgets() {
        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        return List.of(
                new Budget(SpendingCategory.FOOD, new BigDecimal("500"), "EUR", BudgetPeriod.MONTHLY, startDate),
                new Budget(SpendingCategory.TRANSPORT, new BigDecimal("200"), "EUR", BudgetPeriod.MONTHLY, startDate),
                new Budget(SpendingCategory.SHOPPING, new BigDecimal("300"), "EUR", BudgetPeriod.MONTHLY, startDate)
        );
    }
}
