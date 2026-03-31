package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.personalfinance.domain.CashFlowSummary;
import com.voicebanking.agent.personalfinance.domain.PfmTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cash flow calculation service.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class CashFlowService {
    private static final Logger log = LoggerFactory.getLogger(CashFlowService.class);

    public CashFlowSummary calculateCashFlow(List<PfmTransaction> transactions, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> income = transactions.stream()
                .filter(PfmTransaction::isIncome)
                .collect(Collectors.groupingBy(
                        PfmTransaction::currency,
                        Collectors.reducing(BigDecimal.ZERO, PfmTransaction::amount, BigDecimal::add)
                ));

        Map<String, BigDecimal> expenses = transactions.stream()
                .filter(PfmTransaction::isSpending)
                .collect(Collectors.groupingBy(
                        PfmTransaction::currency,
                        Collectors.reducing(BigDecimal.ZERO, PfmTransaction::spendingAmount, BigDecimal::add)
                ));

        Map<String, BigDecimal> net = income.keySet().stream()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> income.getOrDefault(key, BigDecimal.ZERO)
                                .subtract(expenses.getOrDefault(key, BigDecimal.ZERO))
                ));

        log.info("Calculated cash flow for {} transactions", transactions.size());
        return new CashFlowSummary(start, end, income, expenses, net);
    }
}
