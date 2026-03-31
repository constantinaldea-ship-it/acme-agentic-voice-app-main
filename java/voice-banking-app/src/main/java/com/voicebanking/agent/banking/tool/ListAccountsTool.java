package com.voicebanking.agent.banking.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * ADK Tool: List Accounts
 * 
 * <p>Lists all bank accounts for the authenticated user.</p>
 * 
 * <p>Note: ADK FunctionTool requires static methods, so we use a holder pattern
 * with a static service reference set at initialization.</p>
 */
@Configuration
@Profile("cloud")
public class ListAccountsTool {

    private static final Logger log = LoggerFactory.getLogger(ListAccountsTool.class);
    
    // Static reference for tool execution (set at startup)
    private static BankingService bankingServiceRef;

    @Bean
    public FunctionTool listAccountsFunctionTool(BankingService bankingService) throws NoSuchMethodException {
        // Set static reference for tool execution
        bankingServiceRef = bankingService;
        
        // Create FunctionTool from static method
        return FunctionTool.create(
            ListAccountsTool.class.getMethod("listAccounts")
        );
    }

    /**
     * Static tool method for ADK FunctionTool.
     * Lists all bank accounts for the user.
     */
    @Schema(name = "listAccounts", description = "List all bank accounts for the user. Returns account IDs, types (checking, savings, card), names, and last 4 digits.")
    public static List<Account> listAccounts() {
        log.info("ListAccountsTool executing");
        
        List<Account> accounts = bankingServiceRef.getAccounts();
        
        log.info("ListAccountsTool completed: {} accounts found", accounts.size());
        
        return accounts;
    }
}
