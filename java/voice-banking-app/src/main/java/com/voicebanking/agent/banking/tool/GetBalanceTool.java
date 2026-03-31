package com.voicebanking.agent.banking.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.voicebanking.agent.banking.domain.Balance;
import com.voicebanking.agent.banking.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;
import java.util.Optional;

/**
 * ADK Tool: Get Balance
 * 
 * <p>Retrieves the current balance for a bank account.</p>
 * 
 * <p>Note: ADK FunctionTool requires static methods, so we use a holder pattern
 * with a static service reference set at initialization.</p>
 */
@Configuration
@Profile("cloud")
public class GetBalanceTool {

    private static final Logger log = LoggerFactory.getLogger(GetBalanceTool.class);
    
    // Static reference for tool execution (set at startup)
    private static BankingService bankingServiceRef;

    @Bean
    public FunctionTool getBalanceFunctionTool(BankingService bankingService) throws NoSuchMethodException {
        // Set static reference for tool execution
        bankingServiceRef = bankingService;
        
        // Create FunctionTool from static method
        return FunctionTool.create(
            GetBalanceTool.class.getMethod("getBalance", String.class)
        );
    }

    /**
     * Static tool method for ADK FunctionTool.
     * Gets the current balance for a bank account.
     */
    @Schema(name = "getBalance", description = "Get the current balance for a bank account. Returns available balance, pending amount, and currency.")
    public static Balance getBalance(
            @Schema(description = "Account ID to query balance for. Defaults to primary checking account if not specified.")
            String accountId) {
        
        // Default to primary checking if not specified
        if (accountId == null || accountId.isBlank()) {
            accountId = "acc-checking-001";
            log.debug("No accountId provided, using default: {}", accountId);
        }
        
        log.info("GetBalanceTool executing for account: {}", accountId);
        
        Optional<Balance> balance = bankingServiceRef.getBalance(accountId);
        
        if (balance.isEmpty()) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        
        log.info("GetBalanceTool completed: available={} {}", 
            balance.get().available(), balance.get().currency());
        
        return balance.get();
    }
}
