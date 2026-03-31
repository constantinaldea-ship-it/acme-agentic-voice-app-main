package com.voicebanking.agent.creditcard.service;

import com.voicebanking.agent.creditcard.domain.*;
import com.voicebanking.agent.creditcard.integration.CardManagementClient;
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
 * Service for credit card operations.
 * Provides business logic on top of CardManagementClient data.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class CreditCardService {
    private static final Logger log = LoggerFactory.getLogger(CreditCardService.class);
    private static final int DEFAULT_TRANSACTION_LIMIT = 50;

    private final CardManagementClient cardManagementClient;

    public CreditCardService(CardManagementClient cardManagementClient) {
        this.cardManagementClient = cardManagementClient;
    }

    /**
     * Get all credit cards for a customer.
     */
    public List<CreditCard> getCustomerCards(String customerId) {
        log.debug("Getting cards for customer: {}", customerId);
        return cardManagementClient.getCards(customerId);
    }

    /**
     * Get a specific credit card by ID.
     */
    public Optional<CreditCard> getCard(String cardId) {
        return cardManagementClient.getCard(cardId);
    }

    /**
     * Get balance information for a credit card.
     * Returns balance with the associated card info for context.
     */
    public Optional<CreditCardBalance> getBalance(String cardId) {
        log.debug("Getting balance for card: {}", maskCardId(cardId));
        return cardManagementClient.getBalance(cardId);
    }

    /**
     * Get transactions for a credit card.
     * @param cardId The card identifier
     * @param startDate Start of date range (defaults to 30 days ago if null)
     * @param endDate End of date range (defaults to today if null)
     * @param limit Maximum transactions (defaults to 50)
     * @param offset Pagination offset
     * @return List of transactions
     */
    public List<CreditCardTransaction> getTransactions(
            String cardId,
            LocalDate startDate,
            LocalDate endDate,
            Integer limit,
            Integer offset) {
        
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        int effectiveLimit = limit != null ? Math.min(limit, DEFAULT_TRANSACTION_LIMIT) : DEFAULT_TRANSACTION_LIMIT;
        int effectiveOffset = offset != null ? offset : 0;

        log.debug("Getting transactions for card: {}, from {} to {}", 
                maskCardId(cardId), effectiveStart, effectiveEnd);
        
        return cardManagementClient.getTransactions(
                cardId, effectiveStart, effectiveEnd, effectiveLimit, effectiveOffset);
    }

    /**
     * Get transactions filtered by category.
     */
    public List<CreditCardTransaction> getTransactionsByCategory(
            String cardId,
            SpendingCategory category,
            LocalDate startDate,
            LocalDate endDate) {
        
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        
        log.debug("Getting {} transactions for card: {}", category, maskCardId(cardId));
        
        return cardManagementClient.getTransactionsByCategory(
                cardId, category, effectiveStart, effectiveEnd);
    }

    /**
     * Get a statement for a specific period.
     * @param cardId The card identifier
     * @param period Period in format YYYY-MM (e.g., "2026-01") or null for current month
     */
    public Optional<CreditCardStatement> getStatement(String cardId, String period) {
        YearMonth yearMonth = period != null 
                ? YearMonth.parse(period) 
                : YearMonth.now();
        
        log.debug("Getting statement for card: {}, period: {}", maskCardId(cardId), yearMonth);
        
        return cardManagementClient.getStatement(cardId, yearMonth.getYear(), yearMonth.getMonthValue());
    }

    /**
     * Get credit limit information.
     */
    public Optional<CreditCardLimit> getLimit(String cardId) {
        log.debug("Getting limit for card: {}", maskCardId(cardId));
        return cardManagementClient.getLimit(cardId);
    }

    /**
     * Get rewards balance and information.
     */
    public Optional<CreditCardRewards> getRewards(String cardId) {
        log.debug("Getting rewards for card: {}", maskCardId(cardId));
        return cardManagementClient.getRewards(cardId);
    }

    /**
     * Get spending breakdown by category for a period.
     */
    public Map<SpendingCategory, SpendingSummary> getSpendingByCategory(
            String cardId,
            LocalDate startDate,
            LocalDate endDate) {
        
        List<CreditCardTransaction> txns = getTransactions(cardId, startDate, endDate, 100, 0);
        
        BigDecimal totalSpending = txns.stream()
                .filter(CreditCardTransaction::isPurchase)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return txns.stream()
                .filter(CreditCardTransaction::isPurchase)
                .collect(Collectors.groupingBy(
                        CreditCardTransaction::getCategory,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    BigDecimal amount = list.stream()
                                            .map(t -> t.getAmount().abs())
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal percentage = totalSpending.compareTo(BigDecimal.ZERO) > 0
                                            ? amount.multiply(BigDecimal.valueOf(100))
                                                    .divide(totalSpending, 1, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO;
                                    return new SpendingSummary(
                                            amount,
                                            list.size(),
                                            percentage
                                    );
                                }
                        )
                ));
    }

    /**
     * Get top merchants by spending amount.
     */
    public List<MerchantSummary> getTopMerchants(String cardId, LocalDate startDate, LocalDate endDate, int limit) {
        List<CreditCardTransaction> txns = getTransactions(cardId, startDate, endDate, 100, 0);
        
        return txns.stream()
                .filter(CreditCardTransaction::isPurchase)
                .filter(t -> t.getMerchantName() != null && !t.getMerchantName().isEmpty())
                .collect(Collectors.groupingBy(
                        CreditCardTransaction::getMerchantName,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    BigDecimal amount = list.stream()
                                            .map(t -> t.getAmount().abs())
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return new MerchantSummary(
                                            list.get(0).getMerchantName(),
                                            amount,
                                            list.size()
                                    );
                                }
                        )
                ))
                .values().stream()
                .sorted((a, b) -> b.totalAmount().compareTo(a.totalAmount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Format a voice response for balance inquiry.
     */
    public String formatBalanceForVoice(String cardId) {
        Optional<CreditCard> cardOpt = getCard(cardId);
        Optional<CreditCardBalance> balanceOpt = getBalance(cardId);
        
        if (cardOpt.isEmpty() || balanceOpt.isEmpty()) {
            return "I couldn't find that credit card. Please check the card ID and try again.";
        }
        
        return balanceOpt.get().formatForVoice(cardOpt.get());
    }

    /**
     * Format a voice response for transactions.
     */
    public String formatTransactionsForVoice(String cardId, LocalDate startDate, LocalDate endDate) {
        Optional<CreditCard> cardOpt = getCard(cardId);
        List<CreditCardTransaction> txns = getTransactions(cardId, startDate, endDate, 10, 0);
        
        if (cardOpt.isEmpty()) {
            return "I couldn't find that credit card.";
        }
        
        if (txns.isEmpty()) {
            return String.format("You have no transactions on your %s in the selected period.", 
                    cardOpt.get().getFriendlyName());
        }
        
        CreditCard card = cardOpt.get();
        BigDecimal total = txns.stream()
                .filter(CreditCardTransaction::isPurchase)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        CreditCardTransaction largest = txns.stream()
                .filter(CreditCardTransaction::isPurchase)
                .max((a, b) -> a.getAmount().abs().compareTo(b.getAmount().abs()))
                .orElse(null);
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("You had %d transactions on your %s, totaling €%.0f. ", 
                txns.size(), card.getFriendlyName(), total.doubleValue()));
        
        if (largest != null) {
            sb.append(String.format("Your largest purchase was %s. ", largest.formatForVoice()));
        }
        
        sb.append("Would you like more details?");
        
        return sb.toString();
    }

    /**
     * Mask card ID for logging.
     */
    private String maskCardId(String cardId) {
        if (cardId == null || cardId.length() < 4) {
            return "****";
        }
        return "****" + cardId.substring(cardId.length() - 4);
    }

    /**
     * Summary of spending for a category.
     */
    public record SpendingSummary(
            BigDecimal totalAmount,
            int transactionCount,
            BigDecimal percentageOfTotal
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "totalAmount", totalAmount,
                    "transactionCount", transactionCount,
                    "percentageOfTotal", percentageOfTotal
            );
        }
    }

    /**
     * Summary of spending at a merchant.
     */
    public record MerchantSummary(
            String merchantName,
            BigDecimal totalAmount,
            int transactionCount
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "merchantName", merchantName,
                    "totalAmount", totalAmount,
                    "transactionCount", transactionCount
            );
        }
    }
}
