package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.personalfinance.domain.PfmTransaction;
import com.voicebanking.agent.personalfinance.domain.UnusualActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Unusual activity detection service (statistical threshold).
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class UnusualActivityService {
    private static final Logger log = LoggerFactory.getLogger(UnusualActivityService.class);

    public List<UnusualActivity> detectUnusual(List<PfmTransaction> transactions) {
        List<PfmTransaction> spending = transactions.stream().filter(PfmTransaction::isSpending).toList();
        if (spending.isEmpty()) {
            return List.of();
        }

        BigDecimal average = spending.stream()
                .map(PfmTransaction::spendingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(spending.size()), MathContext.DECIMAL64);

        BigDecimal variance = spending.stream()
                .map(t -> t.spendingAmount().subtract(average).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(spending.size()), MathContext.DECIMAL64);

        BigDecimal stdDev = new BigDecimal(Math.sqrt(variance.doubleValue()), MathContext.DECIMAL64);
        BigDecimal threshold = average.add(stdDev.multiply(BigDecimal.valueOf(2)));

        List<UnusualActivity> unusual = new ArrayList<>();
        for (PfmTransaction transaction : spending) {
            if (transaction.spendingAmount().compareTo(threshold) > 0) {
                unusual.add(new UnusualActivity(
                        transaction.date(),
                        transaction.merchantName(),
                        transaction.spendingAmount(),
                        transaction.currency(),
                        "amount_above_average"
                ));
            }
        }

        log.info("Detected {} unusual transactions", unusual.size());
        return unusual;
    }
}
