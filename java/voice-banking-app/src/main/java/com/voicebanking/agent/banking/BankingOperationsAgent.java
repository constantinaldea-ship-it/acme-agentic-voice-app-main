package com.voicebanking.agent.banking;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.domain.Balance;
import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.agent.banking.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banking Operations Agent
 * 
 * Functional agent responsible for core banking operations:
 * - Account balance inquiries
 * - Account listing
 * - Transaction history and querying
 * 
 * Architecture Reference: Component E (AI Functional Agents) - Banking domain
 * 
 * @author Augment Agent
 * @since 2026-01-22
 */
@Component
public class BankingOperationsAgent implements Agent {
    
    private static final Logger log = LoggerFactory.getLogger(BankingOperationsAgent.class);
    
    private static final String AGENT_ID = "banking-operations";
    private static final List<String> TOOL_IDS = List.of(
            "getBalance",
            "listAccounts",
            "queryTransactions"
    );
    
    private final BankingService bankingService;
    
    public BankingOperationsAgent(BankingService bankingService) {
        this.bankingService = bankingService;
        log.info("BankingOperationsAgent initialized");
    }
    
    @Override
    public String getAgentId() {
        return AGENT_ID;
    }
    
    @Override
    public String getDescription() {
        return "Handles core banking operations including balance inquiries, account listing, and transaction queries";
    }
    
    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }
    
    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("BankingOperationsAgent executing tool: {} with input: {}", toolId, input);
        
        return switch (toolId) {
            case "getBalance" -> executeGetBalance(input);
            case "listAccounts" -> executeListAccounts(input);
            case "queryTransactions" -> executeQueryTransactions(input);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolId);
        };
    }
    
    private Map<String, Object> executeGetBalance(Map<String, Object> input) {
        String inputAccountId = (String) input.get("accountId");
        final String accountId = (inputAccountId == null || inputAccountId.isBlank()) ? 
                "acc-checking-001" : inputAccountId;
        
        Balance balance = bankingService.getBalance(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        
        Map<String, Object> result = new HashMap<>();
        result.put("accountId", balance.accountId());
        result.put("available", balance.available());
        result.put("current", balance.current());
        result.put("currency", balance.currency());
        result.put("asOf", balance.asOf());
        return result;
    }
    
    private Map<String, Object> executeListAccounts(Map<String, Object> input) {
        List<Account> accounts = bankingService.getAccounts();
        Map<String, Object> result = new HashMap<>();
        result.put("accounts", accounts);
        return result;
    }
    
    private Map<String, Object> executeQueryTransactions(Map<String, Object> input) {
        String inputAccountId = (String) input.get("accountId");
        final String accountId = (inputAccountId == null || inputAccountId.isBlank()) ? 
                "acc-checking-001" : inputAccountId;
        
        Integer limit = input.containsKey("limit") ?
                ((Number) input.get("limit")).intValue() : 10;
        
        String dateFromStr = (String) input.get("dateFrom");
        String dateToStr = (String) input.get("dateTo");
        String textQuery = (String) input.get("textQuery");
        
        Instant dateFrom = dateFromStr != null ? Instant.parse(dateFromStr) : null;
        Instant dateTo = dateToStr != null ? Instant.parse(dateToStr) : null;
        
        List<Transaction> transactions = bankingService.queryTransactions(
                accountId, dateFrom, dateTo, textQuery, limit);
        
        Map<String, Object> result = new HashMap<>();
        result.put("transactions", transactions);
        return result;
    }
}
