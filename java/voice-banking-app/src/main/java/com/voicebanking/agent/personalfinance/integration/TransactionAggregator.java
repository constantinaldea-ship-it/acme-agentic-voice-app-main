package com.voicebanking.agent.personalfinance.integration;

import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.agent.personalfinance.domain.PfmTransaction;
import com.voicebanking.agent.personalfinance.domain.SpendingCategory;
import com.voicebanking.agent.personalfinance.service.TransactionCategorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregates transactions from banking and credit card contexts.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
public class TransactionAggregator {
    private static final Logger log = LoggerFactory.getLogger(TransactionAggregator.class);

    private final BankingOperationsClient bankingClient;
    private final CreditCardContextClient creditCardClient;
    private final TransactionCategorizer categorizer;

    public TransactionAggregator(
            BankingOperationsClient bankingClient,
            CreditCardContextClient creditCardClient,
            TransactionCategorizer categorizer) {
        this.bankingClient = bankingClient;
        this.creditCardClient = creditCardClient;
        this.categorizer = categorizer;
    }

    public List<PfmTransaction> aggregateTransactions(
            String customerId,
            LocalDate periodStart,
            LocalDate periodEnd) {
        List<PfmTransaction> aggregated = new ArrayList<>();

        Instant startInstant = periodStart != null
                ? periodStart.atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;
        Instant endInstant = periodEnd != null
                ? periodEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusSeconds(1)
                : null;

        List<Account> accounts = bankingClient.listAccounts();
        for (Account account : accounts) {
            List<Transaction> transactions = bankingClient.queryTransactions(
                    account.id(), startInstant, endInstant, 200);
            for (Transaction transaction : transactions) {
                SpendingCategory category = categorizer.categorize(transaction);
                LocalDate date = LocalDate.ofInstant(transaction.date(), ZoneId.systemDefault());
                aggregated.add(new PfmTransaction(
                        "BANK",
                        transaction.id(),
                        date,
                        transaction.description(),
                        transaction.description(),
                        transaction.amount(),
                        transaction.currency(),
                        category
                ));
            }
        }

        List<String> cardIds = creditCardClient.getCardIds(customerId);
        for (String cardId : cardIds) {
            List<Map<String, Object>> txns = creditCardClient.getTransactions(cardId, periodStart, periodEnd);
            for (Map<String, Object> txn : txns) {
                String merchant = stringValue(txn.get("merchantName"));
                String description = stringValue(txn.get("description"));
                String currency = stringValue(txn.get("currency"));
                BigDecimal amount = decimalValue(txn.get("amount"));
                String categoryValue = stringValue(txn.get("category"));
                SpendingCategory category = categorizer.mapCreditCardCategory(categoryValue);
                LocalDate date = creditCardClient.parseTransactionDate(txn.get("transactionDate"))
                    .map(transactionDateTime -> transactionDateTime.toLocalDate())
                        .orElse(periodEnd != null ? periodEnd : LocalDate.now());

                aggregated.add(new PfmTransaction(
                        "CREDIT_CARD",
                        stringValue(txn.get("transactionId")),
                        date,
                        description,
                        merchant,
                        amount,
                        currency,
                        category
                ));
            }
        }

        log.info("Aggregated {} transactions for personal finance analysis", aggregated.size());
        return aggregated;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number num) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        try {
            return value != null ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
