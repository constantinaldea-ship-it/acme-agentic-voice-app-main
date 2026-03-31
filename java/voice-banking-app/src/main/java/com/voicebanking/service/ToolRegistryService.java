package com.voicebanking.service;

import com.voicebanking.agent.banking.domain.Account;
import com.voicebanking.agent.banking.domain.Balance;
import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.agent.banking.service.BankingService;
import com.voicebanking.agent.location.domain.Branch;
import com.voicebanking.agent.location.service.BranchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Tool Registry Service
 * 
 * <p>Manages tool definitions and dispatches tool execution to appropriate services.</p>
 * 
 * <p>Tools:</p>
 * <ul>
 *   <li>getBalance - Get account balance</li>
 *   <li>listAccounts - List all accounts</li>
 *   <li>queryTransactions - Query transactions with filters</li>
 * </ul>
 * 
 * @deprecated Use {@link com.voicebanking.agent.AgentRegistry#executeTool(String, Map)} instead.
 *             This flat tool registry is superseded by the Root Agent Orchestration Pattern
 *             (ADR-BFA-006) which routes through AgentRegistry to preserve agent context.
 *             This class will be removed after migration is complete.
 * @see com.voicebanking.agent.AgentRegistry
 */
@Deprecated(since = "2026-02", forRemoval = true)
@Service
public class ToolRegistryService {
    
    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);
    
    private final BankingService bankingService;
    private final BranchService branchService;
    private final Map<String, Function<Map<String, Object>, Object>> tools;
    
    public ToolRegistryService(BankingService bankingService, BranchService branchService) {
        this.bankingService = bankingService;
        this.branchService = branchService;
        this.tools = registerTools();
        log.info("ToolRegistryService initialized with {} tools", tools.size());
    }
    
    /**
     * Execute a tool call
     * 
     * @param toolName Tool name
     * @param input Tool input parameters
     * @return Tool result (JSON-compatible object)
     * @throws IllegalArgumentException if tool not found
     */
    public Object executeTool(String toolName, Map<String, Object> input) {
        log.info("Executing tool: {} with input: {}", toolName, input);
        
        Function<Map<String, Object>, Object> tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        
        try {
            Object result = tool.apply(input);
            log.info("Tool {} executed successfully", toolName);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a tool exists
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }
    
    /**
     * Get all tool names
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
    
    // ============================================================
    // Tool Registration
    // ============================================================
    
    private Map<String, Function<Map<String, Object>, Object>> registerTools() {
        var toolMap = new HashMap<String, Function<Map<String, Object>, Object>>();
        
        toolMap.put("getBalance", this::getBalanceTool);
        toolMap.put("listAccounts", this::listAccountsTool);
        toolMap.put("queryTransactions", this::queryTransactionsTool);
        toolMap.put("findNearbyBranches", this::findNearbyBranchesTool);
        
        return Collections.unmodifiableMap(toolMap);
    }
    
    // ============================================================
    // Tool Implementations
    // ============================================================
    
    /**
     * Get Balance Tool
     * 
     * Input: { accountId?: string }
     * Output: Balance
     */
    private Object getBalanceTool(Map<String, Object> input) {
        String accountId = (String) input.get("accountId");
        
        // If no accountId specified, use default checking account
        if (accountId == null || accountId.isBlank()) {
            accountId = "acc-checking-001";
            log.debug("No accountId provided, using default: {}", accountId);
        }
        
        Optional<Balance> balance = bankingService.getBalance(accountId);
        if (balance.isEmpty()) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }
        
        return balance.get();
    }
    
    /**
     * List Accounts Tool
     * 
     * Input: {}
     * Output: List<Account>
     */
    private Object listAccountsTool(Map<String, Object> input) {
        return bankingService.getAccounts();
    }
    
    /**
     * Query Transactions Tool
     * 
     * Input: { 
     *   accountId?: string,
     *   limit?: number,
     *   dateFrom?: string (ISO 8601),
     *   dateTo?: string (ISO 8601),
     *   textQuery?: string
     * }
     * Output: List<Transaction>
     */
    private Object queryTransactionsTool(Map<String, Object> input) {
        String accountId = (String) input.get("accountId");
        
        // If no accountId specified, use default checking account
        if (accountId == null || accountId.isBlank()) {
            accountId = "acc-checking-001";
            log.debug("No accountId provided, using default: {}", accountId);
        }
        
        Integer limit = input.containsKey("limit") ? 
            ((Number) input.get("limit")).intValue() : 10;
        
        String dateFromStr = (String) input.get("dateFrom");
        String dateToStr = (String) input.get("dateTo");
        String textQuery = (String) input.get("textQuery");
        
        Instant dateFrom = dateFromStr != null ? Instant.parse(dateFromStr) : null;
        Instant dateTo = dateToStr != null ? Instant.parse(dateToStr) : null;
        
        return bankingService.queryTransactions(accountId, dateFrom, dateTo, textQuery, limit);
    }
    
    /**
     * Find Nearby Branches Tool
     * 
     * Input: { 
     *   latitude: number,
     *   longitude: number,
     *   radiusKm?: number,
     *   limit?: number,
     *   type?: string ("branch", "atm", "flagship", "all")
     * }
     * Output: List<Branch>
     */
    private Object findNearbyBranchesTool(Map<String, Object> input) {
        Double latitude = input.containsKey("latitude") ? 
            ((Number) input.get("latitude")).doubleValue() : null;
        Double longitude = input.containsKey("longitude") ? 
            ((Number) input.get("longitude")).doubleValue() : null;
        
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                "Location is required. Please provide latitude and longitude coordinates.");
        }
        
        double radiusKm = input.containsKey("radiusKm") ? 
            ((Number) input.get("radiusKm")).doubleValue() : 5.0;
        int limit = input.containsKey("limit") ? 
            ((Number) input.get("limit")).intValue() : 5;
        String type = (String) input.getOrDefault("type", "all");
        
        return branchService.findNearby(latitude, longitude, radiusKm, limit, type);
    }
}
