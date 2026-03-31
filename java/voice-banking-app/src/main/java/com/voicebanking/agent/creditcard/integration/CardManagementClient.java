package com.voicebanking.agent.creditcard.integration;

import com.voicebanking.agent.creditcard.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Interface for Card Management System integration.
 * Production implementations would connect to the real Card Management API (I-05).
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public interface CardManagementClient {

    /**
     * Get all credit cards for a customer.
     * @param customerId The customer identifier
     * @return List of credit cards
     */
    List<CreditCard> getCards(String customerId);

    /**
     * Get a specific credit card by ID.
     * @param cardId The card identifier
     * @return The credit card, or empty if not found
     */
    Optional<CreditCard> getCard(String cardId);

    /**
     * Get balance information for a credit card.
     * @param cardId The card identifier
     * @return The balance information
     */
    Optional<CreditCardBalance> getBalance(String cardId);

    /**
     * Get transactions for a credit card.
     * @param cardId The card identifier
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param limit Maximum number of transactions to return
     * @param offset Offset for pagination
     * @return List of transactions
     */
    List<CreditCardTransaction> getTransactions(
            String cardId, 
            LocalDate startDate, 
            LocalDate endDate,
            int limit,
            int offset);

    /**
     * Get a statement for a specific period.
     * @param cardId The card identifier
     * @param year Statement year
     * @param month Statement month (1-12)
     * @return The statement, or empty if not found
     */
    Optional<CreditCardStatement> getStatement(String cardId, int year, int month);

    /**
     * Get credit limit information.
     * @param cardId The card identifier
     * @return The limit information
     */
    Optional<CreditCardLimit> getLimit(String cardId);

    /**
     * Get rewards balance and information.
     * @param cardId The card identifier
     * @return The rewards information
     */
    Optional<CreditCardRewards> getRewards(String cardId);

    /**
     * Get transactions by category.
     * @param cardId The card identifier
     * @param category The spending category
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of transactions in the category
     */
    List<CreditCardTransaction> getTransactionsByCategory(
            String cardId,
            SpendingCategory category,
            LocalDate startDate,
            LocalDate endDate);
}
