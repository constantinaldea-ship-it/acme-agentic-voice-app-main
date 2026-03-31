package com.voicebanking.agent.creditcard;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.creditcard.domain.*;
import com.voicebanking.agent.creditcard.service.CreditCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CreditCardContextAgent
 * 
 * Provides detailed credit card activity information, spending insights, and card 
 * management capabilities. Integrates with the Card Management API (I-05) to retrieve 
 * statements, track rewards, and provide contextual spending analysis.
 * 
 * This agent supports higher-tier conversational banking use cases (Category 4).
 * 
 * Architecture Reference: Component E (AI Functional Agents) from Acme Bank Architecture
 * Implementation Plan: AGENT-006-credit-card-context.md
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
public class CreditCardContextAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(CreditCardContextAgent.class);

    private static final String AGENT_ID = "credit-card-context";
    private static final List<String> TOOL_IDS = List.of(
            "getCreditCardBalance",
            "getCreditCardTransactions",
            "getCreditCardStatement",
            "getCreditCardLimit",
            "getCreditCardRewards"
    );

    private final CreditCardService creditCardService;

    public CreditCardContextAgent(CreditCardService creditCardService) {
        this.creditCardService = creditCardService;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Provides credit card activity information, spending insights, and rewards tracking";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("Executing tool: {} with input keys: {}", toolId, input.keySet());

        try {
            return switch (toolId) {
                case "getCreditCardBalance" -> getCreditCardBalance(input);
                case "getCreditCardTransactions" -> getCreditCardTransactions(input);
                case "getCreditCardStatement" -> getCreditCardStatement(input);
                case "getCreditCardLimit" -> getCreditCardLimit(input);
                case "getCreditCardRewards" -> getCreditCardRewards(input);
                default -> errorResponse("UNKNOWN_TOOL", "Unknown tool: " + toolId);
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return errorResponse("EXECUTION_ERROR", e.getMessage());
        }
    }

    /**
     * Get credit card balance information.
     * 
     * Input:
     *   - cardId: String (required) - The credit card identifier
     *   - customerId: String (optional) - Customer ID to validate ownership
     * 
     * Output:
     *   - success: boolean
     *   - card: Card information (masked)
     *   - balance: Balance details
     *   - voiceResponse: Pre-formatted voice response
     */
    private Map<String, Object> getCreditCardBalance(Map<String, Object> input) {
        String cardId = (String) input.get("cardId");
        
        if (cardId == null || cardId.isBlank()) {
            return errorResponse("MISSING_CARD_ID", "Card ID is required");
        }

        Optional<CreditCard> cardOpt = creditCardService.getCard(cardId);
        if (cardOpt.isEmpty()) {
            return errorResponse("CARD_NOT_FOUND", "Credit card not found: " + maskCardId(cardId));
        }

        Optional<CreditCardBalance> balanceOpt = creditCardService.getBalance(cardId);
        if (balanceOpt.isEmpty()) {
            return errorResponse("BALANCE_NOT_FOUND", "Balance information not available");
        }

        CreditCard card = cardOpt.get();
        CreditCardBalance balance = balanceOpt.get();

        log.info("Retrieved balance for card: {}", card.getMaskedCardNumber());

        return Map.of(
                "success", true,
                "card", card.toMap(),
                "balance", balance.toMap(),
                "voiceResponse", balance.formatForVoice(card),
                "metadata", buildMetadata("getCreditCardBalance")
        );
    }

    /**
     * Get credit card transactions.
     * 
     * Input:
     *   - cardId: String (required) - The credit card identifier
     *   - startDate: String (optional) - Start date in YYYY-MM-DD format
     *   - endDate: String (optional) - End date in YYYY-MM-DD format
     *   - category: String (optional) - Filter by spending category
     *   - limit: Integer (optional) - Max transactions to return (default 50)
     *   - offset: Integer (optional) - Pagination offset
     * 
     * Output:
     *   - success: boolean
     *   - transactions: List of transaction details
     *   - summary: Transaction summary (count, total)
     *   - voiceResponse: Pre-formatted voice response
     */
    private Map<String, Object> getCreditCardTransactions(Map<String, Object> input) {
        String cardId = (String) input.get("cardId");
        
        if (cardId == null || cardId.isBlank()) {
            return errorResponse("MISSING_CARD_ID", "Card ID is required");
        }

        Optional<CreditCard> cardOpt = creditCardService.getCard(cardId);
        if (cardOpt.isEmpty()) {
            return errorResponse("CARD_NOT_FOUND", "Credit card not found: " + maskCardId(cardId));
        }

        // Parse date range
        LocalDate startDate = parseDate((String) input.get("startDate"), LocalDate.now().minusDays(30));
        LocalDate endDate = parseDate((String) input.get("endDate"), LocalDate.now());
        
        // Parse optional filters
        String categoryStr = (String) input.get("category");
        Integer limit = input.get("limit") instanceof Number 
                ? ((Number) input.get("limit")).intValue() 
                : null;
        Integer offset = input.get("offset") instanceof Number 
                ? ((Number) input.get("offset")).intValue() 
                : null;

        List<CreditCardTransaction> transactions;
        
        if (categoryStr != null && !categoryStr.isBlank()) {
            try {
                SpendingCategory category = SpendingCategory.valueOf(categoryStr.toUpperCase());
                transactions = creditCardService.getTransactionsByCategory(
                        cardId, category, startDate, endDate);
            } catch (IllegalArgumentException e) {
                return errorResponse("INVALID_CATEGORY", "Invalid category: " + categoryStr);
            }
        } else {
            transactions = creditCardService.getTransactions(
                    cardId, startDate, endDate, limit, offset);
        }

        CreditCard card = cardOpt.get();
        
        // Calculate summary
        long purchaseCount = transactions.stream().filter(CreditCardTransaction::isPurchase).count();
        var purchaseTotal = transactions.stream()
                .filter(CreditCardTransaction::isPurchase)
                .map(t -> t.getAmount().abs())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        String voiceResponse = creditCardService.formatTransactionsForVoice(cardId, startDate, endDate);

        log.info("Retrieved {} transactions for card: {}", transactions.size(), card.getMaskedCardNumber());

        return Map.of(
                "success", true,
                "card", card.toMap(),
                "transactions", transactions.stream()
                        .map(CreditCardTransaction::toMap)
                        .collect(Collectors.toList()),
                "summary", Map.of(
                        "totalTransactions", transactions.size(),
                        "purchaseCount", purchaseCount,
                        "purchaseTotal", purchaseTotal,
                        "dateRange", Map.of("start", startDate.toString(), "end", endDate.toString())
                ),
                "voiceResponse", voiceResponse,
                "metadata", buildMetadata("getCreditCardTransactions")
        );
    }

    /**
     * Get credit card statement.
     * 
     * Input:
     *   - cardId: String (required) - The credit card identifier
     *   - period: String (optional) - Statement period in YYYY-MM format (default: current month)
     * 
     * Output:
     *   - success: boolean
     *   - card: Card information
     *   - statement: Statement details with transactions
     *   - voiceResponse: Pre-formatted voice response
     */
    private Map<String, Object> getCreditCardStatement(Map<String, Object> input) {
        String cardId = (String) input.get("cardId");
        String period = (String) input.get("period");
        
        if (cardId == null || cardId.isBlank()) {
            return errorResponse("MISSING_CARD_ID", "Card ID is required");
        }

        Optional<CreditCard> cardOpt = creditCardService.getCard(cardId);
        if (cardOpt.isEmpty()) {
            return errorResponse("CARD_NOT_FOUND", "Credit card not found: " + maskCardId(cardId));
        }

        Optional<CreditCardStatement> statementOpt = creditCardService.getStatement(cardId, period);
        if (statementOpt.isEmpty()) {
            return errorResponse("STATEMENT_NOT_FOUND", "Statement not found for period: " + period);
        }

        CreditCard card = cardOpt.get();
        CreditCardStatement statement = statementOpt.get();

        log.info("Retrieved statement {} for card: {}", 
                statement.getStatementId(), card.getMaskedCardNumber());

        return Map.of(
                "success", true,
                "card", card.toMap(),
                "statement", statement.toMap(),
                "voiceResponse", statement.formatSummaryForVoice(card),
                "metadata", buildMetadata("getCreditCardStatement")
        );
    }

    /**
     * Get credit card limit information.
     * 
     * Input:
     *   - cardId: String (required) - The credit card identifier
     * 
     * Output:
     *   - success: boolean
     *   - card: Card information
     *   - limit: Credit limit details including utilization
     *   - voiceResponse: Pre-formatted voice response
     */
    private Map<String, Object> getCreditCardLimit(Map<String, Object> input) {
        String cardId = (String) input.get("cardId");
        
        if (cardId == null || cardId.isBlank()) {
            return errorResponse("MISSING_CARD_ID", "Card ID is required");
        }

        Optional<CreditCard> cardOpt = creditCardService.getCard(cardId);
        if (cardOpt.isEmpty()) {
            return errorResponse("CARD_NOT_FOUND", "Credit card not found: " + maskCardId(cardId));
        }

        Optional<CreditCardLimit> limitOpt = creditCardService.getLimit(cardId);
        if (limitOpt.isEmpty()) {
            return errorResponse("LIMIT_NOT_FOUND", "Limit information not available");
        }

        CreditCard card = cardOpt.get();
        CreditCardLimit limit = limitOpt.get();

        log.info("Retrieved limit for card: {}", card.getMaskedCardNumber());

        return Map.of(
                "success", true,
                "card", card.toMap(),
                "limit", limit.toMap(),
                "voiceResponse", limit.formatForVoice(card),
                "metadata", buildMetadata("getCreditCardLimit")
        );
    }

    /**
     * Get credit card rewards balance and information.
     * 
     * Input:
     *   - cardId: String (required) - The credit card identifier
     * 
     * Output:
     *   - success: boolean
     *   - card: Card information
     *   - rewards: Rewards balance and redemption options
     *   - voiceResponse: Pre-formatted voice response
     */
    private Map<String, Object> getCreditCardRewards(Map<String, Object> input) {
        String cardId = (String) input.get("cardId");
        
        if (cardId == null || cardId.isBlank()) {
            return errorResponse("MISSING_CARD_ID", "Card ID is required");
        }

        Optional<CreditCard> cardOpt = creditCardService.getCard(cardId);
        if (cardOpt.isEmpty()) {
            return errorResponse("CARD_NOT_FOUND", "Credit card not found: " + maskCardId(cardId));
        }

        Optional<CreditCardRewards> rewardsOpt = creditCardService.getRewards(cardId);
        if (rewardsOpt.isEmpty()) {
            return errorResponse("REWARDS_NOT_FOUND", "Rewards information not available for this card");
        }

        CreditCard card = cardOpt.get();
        CreditCardRewards rewards = rewardsOpt.get();

        log.info("Retrieved rewards for card: {}", card.getMaskedCardNumber());

        return Map.of(
                "success", true,
                "card", card.toMap(),
                "rewards", rewards.toMap(),
                "voiceResponse", rewards.formatForVoice(card),
                "metadata", buildMetadata("getCreditCardRewards")
        );
    }

    /**
     * Parse a date string, returning default if null or invalid.
     */
    private LocalDate parseDate(String dateStr, LocalDate defaultDate) {
        if (dateStr == null || dateStr.isBlank()) {
            return defaultDate;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format: {}, using default", dateStr);
            return defaultDate;
        }
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
     * Build error response map.
     */
    private Map<String, Object> errorResponse(String code, String message) {
        return Map.of(
                "success", false,
                "error", Map.of(
                        "code", code,
                        "message", message
                ),
                "metadata", buildMetadata("error")
        );
    }

    /**
     * Build metadata for responses.
     */
    private Map<String, Object> buildMetadata(String toolId) {
        return Map.of(
                "agentId", AGENT_ID,
                "toolId", toolId,
                "timestamp", java.time.Instant.now().toString()
        );
    }
}
