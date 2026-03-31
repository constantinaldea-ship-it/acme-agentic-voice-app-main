package com.voicebanking.agent.banking.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.agent.banking.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.util.List;

/**
 * ADK Tool: Query Transactions
 * 
 * <p>Queries recent transactions for a bank account with optional filters.</p>
 * 
 * <p>Note: ADK FunctionTool requires static methods, so we use a holder pattern
 * with a static service reference set at initialization.</p>
 */
@Configuration
@Profile("cloud")
public class QueryTransactionsTool {

    private static final Logger log = LoggerFactory.getLogger(QueryTransactionsTool.class);
    private static final int DEFAULT_LIMIT = 10;
    
    // Static reference for tool execution (set at startup)
    private static BankingService bankingServiceRef;

    @Bean
    public FunctionTool queryTransactionsFunctionTool(BankingService bankingService) throws NoSuchMethodException {
        // Set static reference for tool execution
        bankingServiceRef = bankingService;
        
        // Create FunctionTool from static method
        return FunctionTool.create(
            QueryTransactionsTool.class.getMethod(
                "queryTransactions", 
                String.class, Integer.class, String.class, String.class, String.class
            )
        );
    }

    /**
     * Static tool method for ADK FunctionTool.
     * Queries recent transactions for a bank account.
     */
    @Schema(name = "queryTransactions", description = "Query recent transactions for a bank account. Can filter by date range, merchant name, or description text.")
    public static List<Transaction> queryTransactions(
            @Schema(description = "Account ID to query transactions for. Defaults to primary checking account.")
            String accountId,
            
            @Schema(description = "Maximum number of transactions to return. Defaults to 10.")
            Integer limit,
            
            @Schema(description = "Start date filter in ISO 8601 format (e.g., 2026-01-01T00:00:00Z)")
            String dateFrom,
            
            @Schema(description = "End date filter in ISO 8601 format (e.g., 2026-01-31T23:59:59Z)")
            String dateTo,
            
            @Schema(description = "Text filter for merchant name or transaction description (e.g., 'Starbucks', 'grocery')")
            String textQuery) {
        
        // Default to primary checking if not specified
        if (accountId == null || accountId.isBlank()) {
            accountId = "acc-checking-001";
            log.debug("No accountId provided, using default: {}", accountId);
        }
        
        int actualLimit = limit != null ? limit : DEFAULT_LIMIT;
        
        // Parse date filters if provided
        Instant dateFromInstant = parseInstant(dateFrom);
        Instant dateToInstant = parseInstant(dateTo);
        
        log.info("QueryTransactionsTool executing for account: {} (limit={}, textQuery={})", 
            accountId, actualLimit, textQuery);
        
        List<Transaction> transactions = bankingServiceRef.queryTransactions(
            accountId, dateFromInstant, dateToInstant, textQuery, actualLimit
        );
        
        log.info("QueryTransactionsTool completed: {} transactions found", transactions.size());
        
        return transactions;
    }

    private static Instant parseInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }
}
