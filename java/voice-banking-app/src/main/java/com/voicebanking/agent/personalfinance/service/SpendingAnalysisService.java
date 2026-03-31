package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.personalfinance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Spending analysis engine for personal finance insights.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class SpendingAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(SpendingAnalysisService.class);

    public SpendingBreakdown calculateBreakdown(List<PfmTransaction> transactions, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> totalsByCurrency = totalsByCurrency(transactions);

        Map<String, List<PfmTransaction>> byCurrency = transactions.stream()
                .filter(PfmTransaction::isSpending)
                .collect(Collectors.groupingBy(PfmTransaction::currency));

        List<SpendingCategorySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<PfmTransaction>> entry : byCurrency.entrySet()) {
            String currency = entry.getKey();
            List<PfmTransaction> currencyTxns = entry.getValue();
            BigDecimal total = totalsByCurrency.getOrDefault(currency, BigDecimal.ZERO);

            Map<SpendingCategory, List<PfmTransaction>> grouped = currencyTxns.stream()
                    .collect(Collectors.groupingBy(PfmTransaction::category));

            for (Map.Entry<SpendingCategory, List<PfmTransaction>> catEntry : grouped.entrySet()) {
                BigDecimal categoryTotal = catEntry.getValue().stream()
                        .map(PfmTransaction::spendingAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal percent = total.compareTo(BigDecimal.ZERO) > 0
                        ? categoryTotal.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                summaries.add(new SpendingCategorySummary(
                        catEntry.getKey(),
                        categoryTotal,
                        percent,
                        catEntry.getValue().size(),
                        currency
                ));
            }
        }

        log.info("Calculated spending breakdown for {} transactions", transactions.size());
        return new SpendingBreakdown(start, end, totalsByCurrency, summaries);
    }

    public SpendingTrend calculateMonthlyTrend(List<PfmTransaction> transactions, int monthsBack) {
        if (monthsBack <= 0) {
            return new SpendingTrend(List.of());
        }

        List<TrendPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = monthsBack - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();

            Map<String, BigDecimal> totals = totalsByCurrency(transactions.stream()
                    .filter(t -> !t.date().isBefore(start) && !t.date().isAfter(end))
                    .toList());

            for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
                BigDecimal total = entry.getValue();
                BigDecimal prior = findPreviousMonthTotal(points, entry.getKey());
                BigDecimal change = prior != null ? total.subtract(prior) : BigDecimal.ZERO;
                BigDecimal changePercent = (prior != null && prior.compareTo(BigDecimal.ZERO) > 0)
                        ? change.multiply(BigDecimal.valueOf(100)).divide(prior, 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                points.add(new TrendPoint(month, total, change, changePercent, entry.getKey()));
            }
        }

        return new SpendingTrend(points);
    }

    public List<Map<String, Object>> getTopMerchants(List<PfmTransaction> transactions, int limit) {
        return transactions.stream()
                .filter(PfmTransaction::isSpending)
                .filter(t -> t.merchantName() != null && !t.merchantName().isBlank())
                .collect(Collectors.groupingBy(t -> t.merchantName().trim()))
                .values().stream()
                .<Map<String, Object>>map(list -> {
                    BigDecimal total = list.stream()
                            .map(PfmTransaction::spendingAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    String currency = list.stream().map(PfmTransaction::currency).filter(Objects::nonNull).findFirst().orElse("EUR");
                    Map<String, Object> result = new HashMap<>();
                    result.put("merchantName", list.get(0).merchantName());
                    result.put("totalAmount", total);
                    result.put("transactionCount", list.size());
                    result.put("currency", currency);
                    return result;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Map<String, BigDecimal> totalsByCurrency(List<PfmTransaction> transactions) {
        return transactions.stream()
                .filter(PfmTransaction::isSpending)
                .collect(Collectors.groupingBy(
                        PfmTransaction::currency,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                PfmTransaction::spendingAmount,
                                BigDecimal::add
                        )
                ));
    }

    private BigDecimal findPreviousMonthTotal(List<TrendPoint> points, String currency) {
        for (int i = points.size() - 1; i >= 0; i--) {
            TrendPoint point = points.get(i);
            if (point.currency().equals(currency)) {
                return point.totalSpending();
            }
        }
        return null;
    }
}
