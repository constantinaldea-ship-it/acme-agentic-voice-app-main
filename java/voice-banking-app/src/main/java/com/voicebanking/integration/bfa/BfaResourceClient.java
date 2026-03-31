package com.voicebanking.integration.bfa;

import com.voicebanking.integration.bfa.BfaDto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * REST client for BFA Resource-Oriented API.
 * 
 * <p>Provides typed access to the bfa-service-resource endpoints, replacing
 * the untyped {@code AgentRegistry.executeTool()} pattern with proper DTOs.</p>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Autowired BfaResourceClient bfaClient;
 * 
 * // Get all accounts
 * List<AccountDto> accounts = bfaClient.listAccounts();
 * 
 * // Get balance for specific account
 * BalanceDto balance = bfaClient.getAccountBalance("ACC-001");
 * 
 * // Query transactions
 * List<TransactionDto> transactions = bfaClient.getAccountTransactions(
 *     "ACC-001", 
 *     Instant.now().minus(30, ChronoUnit.DAYS),
 *     Instant.now(),
 *     100
 * );
 * }</pre>
 * 
 * <h2>Headers</h2>
 * <p>The client automatically includes:</p>
 * <ul>
 *   <li>{@code Authorization} - Bearer token from context</li>
 *   <li>{@code X-Correlation-ID} - From MDC or generated</li>
 *   <li>{@code X-Agent-Id} - From MDC if available</li>
 *   <li>{@code X-Session-Id} - From MDC if available</li>
 * </ul>
 *
 * @author Augment Agent
 * @since 2026-02-02
 * @see BfaDto
 */
@Component
public class BfaResourceClient {
    
    private static final Logger log = LoggerFactory.getLogger(BfaResourceClient.class);
    
    private final RestClient restClient;
    private final String baseUrl;
    
    public BfaResourceClient(
            RestClient.Builder restClientBuilder,
            @Value("${bfa.resource.base-url:http://localhost:8082}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    log.error("BFA API error: {} {} -> {}", 
                            request.getMethod(), request.getURI(), response.getStatusCode());
                })
                .build();
        log.info("BfaResourceClient initialized with base URL: {}", baseUrl);
    }
    
    // ========================================
    // Account Operations
    // ========================================
    
    /**
     * List all accounts for the authenticated user.
     *
     * @return list of accounts
     */
    public List<AccountDto> listAccounts() {
        log.debug("Listing accounts");
        ApiResponse<List<AccountDto>> response = restClient.get()
                .uri("/api/v1/accounts")
                .headers(this::addAgentHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        
        return extractData(response, "listAccounts");
    }
    
    /**
     * Get account details by ID.
     *
     * @param accountId account identifier
     * @return account details or empty if not found
     */
    public Optional<AccountDto> getAccount(String accountId) {
        log.debug("Getting account: {}", accountId);
        try {
            ApiResponse<AccountDto> response = restClient.get()
                    .uri("/api/v1/accounts/{id}", accountId)
                    .headers(this::addAgentHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            
            return Optional.ofNullable(extractData(response, "getAccount"));
        } catch (Exception e) {
            log.warn("Account not found: {}", accountId);
            return Optional.empty();
        }
    }
    
    /**
     * Get account balance.
     *
     * @param accountId account identifier
     * @return balance information
     */
    public BalanceDto getAccountBalance(String accountId) {
        log.debug("Getting balance for account: {}", accountId);
        ApiResponse<BalanceDto> response = restClient.get()
                .uri("/api/v1/accounts/{id}/balance", accountId)
                .headers(this::addAgentHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        
        return extractData(response, "getAccountBalance");
    }
    
    /**
     * Query account transactions.
     *
     * @param accountId account identifier
     * @param dateFrom start date (inclusive)
     * @param dateTo end date (inclusive)
     * @param limit maximum number of transactions
     * @return list of transactions
     */
    public List<TransactionDto> getAccountTransactions(String accountId, Instant dateFrom, Instant dateTo, Integer limit) {
        log.debug("Getting transactions for account: {} from {} to {}", accountId, dateFrom, dateTo);
        
        ApiResponse<List<TransactionDto>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/accounts/{id}/transactions")
                        .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom).map(Instant::toString))
                        .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo).map(Instant::toString))
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build(accountId))
                .headers(this::addAgentHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        
        return extractData(response, "getAccountTransactions");
    }
    
    // ========================================
    // Card Operations
    // ========================================
    
    /**
     * List all cards for the authenticated user.
     *
     * @return list of cards
     */
    public List<CardDto> listCards() {
        log.debug("Listing cards");
        ApiResponse<List<CardDto>> response = restClient.get()
                .uri("/api/v1/cards")
                .headers(this::addAgentHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        
        return extractData(response, "listCards");
    }
    
    /**
     * Get card details by ID.
     *
     * @param cardId card identifier
     * @return card details or empty if not found
     */
    public Optional<CardDto> getCard(String cardId) {
        log.debug("Getting card: {}", cardId);
        try {
            ApiResponse<CardDto> response = restClient.get()
                    .uri("/api/v1/cards/{id}", cardId)
                    .headers(this::addAgentHeaders)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            
            return Optional.ofNullable(extractData(response, "getCard"));
        } catch (Exception e) {
            log.warn("Card not found: {}", cardId);
            return Optional.empty();
        }
    }
    
    /**
     * Get card balance.
     *
     * @param cardId card identifier
     * @return balance information
     */
    public BalanceDto getCardBalance(String cardId) {
        log.debug("Getting balance for card: {}", cardId);
        ApiResponse<BalanceDto> response = restClient.get()
                .uri("/api/v1/cards/{id}/balance", cardId)
                .headers(this::addAgentHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        
        return extractData(response, "getCardBalance");
    }
    
    /**
     * Query card transactions.
     *
     * @param cardId card identifier
     * @param dateFrom start date (inclusive)
     * @param dateTo end date (inclusive)
     * @param limit maximum number of transactions
     * @return list of transactions
     */
    public List<TransactionDto> getCardTransactions(String cardId, Instant dateFrom, Instant dateTo, Integer limit) {
        log.debug("Getting transactions for card: {} from {} to {}", cardId, dateFrom, dateTo);
        
        ApiResponse<List<TransactionDto>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/cards/{id}/transactions")
                        .queryParamIfPresent("dateFrom", Optional.ofNullable(dateFrom).map(Instant::toString))
                        .queryParamIfPresent("dateTo", Optional.ofNullable(dateTo).map(Instant::toString))
                        .queryParamIfPresent("limit", Optional.ofNullable(limit))
                        .build(cardId))
                .headers(this::addAgentHeaders)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        
        return extractData(response, "getCardTransactions");
    }
    
    // ========================================
    // Health Check
    // ========================================
    
    /**
     * Check if BFA service is available.
     *
     * @return true if service is healthy
     */
    public boolean isHealthy() {
        try {
            restClient.get()
                    .uri("/api/v1/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("BFA health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    // ========================================
    // Internal Helpers
    // ========================================
    
    private void addAgentHeaders(org.springframework.http.HttpHeaders headers) {
        // Authorization - get from security context or use default for dev
        String token = getAuthToken();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        
        // Correlation ID
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            headers.set("X-Correlation-ID", correlationId);
        }
        
        // Agent context
        String agentId = MDC.get("agentId");
        if (agentId != null) {
            headers.set("X-Agent-Id", agentId);
        }
        
        String sessionId = MDC.get("sessionId");
        if (sessionId != null) {
            headers.set("X-Session-Id", sessionId);
        }
        
        String toolId = MDC.get("toolId");
        if (toolId != null) {
            headers.set("X-Tool-Id", toolId);
        }
    }
    
    private String getAuthToken() {
        // In production, this would get the token from SecurityContext
        // For now, use a default token for development
        String token = MDC.get("authToken");
        return token != null ? token : "user-test-001";
    }
    
    private <T> T extractData(ApiResponse<T> response, String operation) {
        if (response == null) {
            log.error("Null response from BFA for operation: {}", operation);
            throw new BfaClientException("Null response from BFA service");
        }
        if (!response.success()) {
            String errorMessage = response.error() != null ? response.error().message() : "Unknown error";
            log.error("BFA operation {} failed: {}", operation, errorMessage);
            throw new BfaClientException("BFA operation failed: " + errorMessage);
        }
        return response.data();
    }
    
    /**
     * Exception thrown when BFA API calls fail.
     */
    public static class BfaClientException extends RuntimeException {
        public BfaClientException(String message) {
            super(message);
        }
        
        public BfaClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
