package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.personalfinance.domain.PfmTransaction;
import com.voicebanking.agent.personalfinance.domain.RecurringTransaction;
import com.voicebanking.agent.personalfinance.domain.SpendingCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recurring transaction detection service.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class RecurringDetectionService {
    private static final Logger log = LoggerFactory.getLogger(RecurringDetectionService.class);

    public List<RecurringTransaction> detectRecurring(List<PfmTransaction> transactions) {
        Map<String, List<PfmTransaction>> byMerchant = transactions.stream()
                .filter(PfmTransaction::isSpending)
                .filter(t -> t.merchantName() != null && !t.merchantName().isBlank())
                .collect(Collectors.groupingBy(t -> t.merchantName().trim()));

        List<RecurringTransaction> recurring = new ArrayList<>();

        for (Map.Entry<String, List<PfmTransaction>> entry : byMerchant.entrySet()) {
            List<PfmTransaction> txns = entry.getValue();
            if (txns.size() < 2) {
                continue;
            }

            txns.sort((a, b) -> a.date().compareTo(b.date()));
            long daysBetween = ChronoUnit.DAYS.between(txns.get(0).date(), txns.get(txns.size() - 1).date());
            String frequency = daysBetween >= 25 ? "monthly" : "weekly";

            BigDecimal avg = txns.stream()
                    .map(PfmTransaction::spendingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(txns.size()), 2, RoundingMode.HALF_UP);

            BigDecimal monthlyTotal = frequency.equals("monthly") ? avg : avg.multiply(BigDecimal.valueOf(4));

            SpendingCategory category = txns.get(0).category();
            String currency = txns.get(0).currency();

            recurring.add(new RecurringTransaction(
                    entry.getKey(),
                    avg,
                    currency,
                    frequency,
                    txns.size(),
                    category,
                    monthlyTotal
            ));
        }

        log.info("Detected {} recurring merchants", recurring.size());
        return recurring;
    }
}
