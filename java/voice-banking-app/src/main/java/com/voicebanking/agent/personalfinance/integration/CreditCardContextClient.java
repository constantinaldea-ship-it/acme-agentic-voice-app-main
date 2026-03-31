package com.voicebanking.agent.personalfinance.integration;

import com.voicebanking.agent.AgentRegistry;
import com.voicebanking.agent.creditcard.domain.CreditCard;
import com.voicebanking.agent.creditcard.service.CreditCardService;
import com.voicebanking.integration.bfa.BfaDto.CardDto;
import com.voicebanking.integration.bfa.BfaDto.TransactionDto;
import com.voicebanking.integration.bfa.BfaResourceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Integration client for credit card operations.
 * 
 * <p>This client supports two modes:</p>
 * <ul>
 *   <li><b>BFA Resource Mode</b> (default): Uses typed REST client to call bfa-service-resource</li>
 *   <li><b>Legacy Mode</b>: Uses AgentRegistry.executeTool() for backward compatibility</li>
 * </ul>
 * 
 * <p>Set {@code bfa.resource.enabled=true} to use the new typed REST client.</p>
 *
 * @author Augment Agent
 * @since 2026-01-25
 * @modified 2026-02-02 - Added BfaResourceClient integration
 */
@Component
public class CreditCardContextClient {
    private static final Logger log = LoggerFactory.getLogger(CreditCardContextClient.class);

    private final AgentRegistry agentRegistry;
    private final CreditCardService creditCardService;
    private final BfaResourceClient bfaClient;
    private final boolean useBfaResource;

    public CreditCardContextClient(
            @Lazy AgentRegistry agentRegistry, 
            CreditCardService creditCardService,
            BfaResourceClient bfaClient,
            @Value("${bfa.resource.enabled:true}") boolean useBfaResource) {
        this.agentRegistry = agentRegistry;
        this.creditCardService = creditCardService;
        this.bfaClient = bfaClient;
        this.useBfaResource = useBfaResource;
        log.info("CreditCardContextClient initialized with BFA resource mode: {}", useBfaResource);
    }

    /**
     * Get all card IDs for a customer.
     *
     * @param customerId customer identifier (uses default if null)
     * @return list of card IDs
     */
    public List<String> getCardIds(String customerId) {
        if (useBfaResource) {
            return getCardIdsViaBfa();
        }
        return getCardIdsViaService(customerId);
    }

    /**
     * Get transactions for a card.
     *
     * @param cardId card identifier
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of transaction maps (for backward compatibility)
     */
    public List<Map<String, Object>> getTransactions(String cardId, LocalDate startDate, LocalDate endDate) {
        if (useBfaResource) {
            return getTransactionsViaBfa(cardId, startDate, endDate);
        }
        return getTransactionsViaAgentRegistry(cardId, startDate, endDate);
    }

    /**
     * Parse a transaction date from various formats.
     *
     * @param value date value (String or Instant)
     * @return parsed LocalDateTime or empty
     */
    public Optional<LocalDateTime> parseTransactionDate(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            if (value instanceof Instant instant) {
                return Optional.of(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
            }
            return Optional.of(LocalDateTime.parse(value.toString()));
        } catch (Exception e) {
            log.warn("Unable to parse transaction date: {}", value);
            return Optional.empty();
        }
    }

    // ========================================
    // BFA Resource Client Methods (New)
    // ========================================

    private List<String> getCardIdsViaBfa() {
        log.debug("Getting card IDs via BFA resource client");
        try {
            List<CardDto> cards = bfaClient.listCards();
            return cards.stream()
                    .map(CardDto::id)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get card IDs via BFA: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Map<String, Object>> getTransactionsViaBfa(String cardId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting card transactions via BFA resource client for card: {}", cardId);
        try {
            Instant dateFrom = startDate != null 
                    ? startDate.atStartOfDay(ZoneId.systemDefault()).toInstant() 
                    : null;
            Instant dateTo = endDate != null 
                    ? endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() 
                    : null;
            
            List<TransactionDto> dtos = bfaClient.getCardTransactions(cardId, dateFrom, dateTo, 200);
            
            // Convert to Map<String, Object> for backward compatibility
            return dtos.stream()
                    .map(this::transactionDtoToMap)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get card transactions via BFA: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private Map<String, Object> transactionDtoToMap(TransactionDto dto) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", dto.id());
        map.put("cardId", dto.resourceId());
        map.put("amount", dto.amount());
        map.put("currency", dto.currency());
        map.put("description", dto.description());
        map.put("merchantName", dto.merchantName());
        map.put("category", dto.category());
        map.put("timestamp", dto.timestamp());
        map.put("status", dto.status() != null ? dto.status().name() : null);
        return map;
    }

    // ========================================
    // Legacy Methods (Deprecated)
    // ========================================

    /**
     * @deprecated Use {@link #getCardIdsViaBfa()} via bfa.resource.enabled=true
     */
    @Deprecated(since = "2026-02-02", forRemoval = true)
    private List<String> getCardIdsViaService(String customerId) {
        String effectiveCustomerId = (customerId == null || customerId.isBlank()) ? "CUST-001" : customerId;
        List<CreditCard> cards = creditCardService.getCustomerCards(effectiveCustomerId);
        return cards.stream().map(CreditCard::getCardId).toList();
    }

    /**
     * @deprecated Use {@link #getTransactionsViaBfa(String, LocalDate, LocalDate)} via bfa.resource.enabled=true
     */
    @Deprecated(since = "2026-02-02", forRemoval = true)
    private List<Map<String, Object>> getTransactionsViaAgentRegistry(String cardId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> result = agentRegistry.executeTool("getCreditCardTransactions", Map.of(
                "cardId", cardId,
                "startDate", startDate != null ? startDate.toString() : null,
                "endDate", endDate != null ? endDate.toString() : null,
                "limit", 200,
                "offset", 0
        ));

        Object txObj = result.get("transactions");
        if (txObj instanceof List<?> list) {
            List<Map<String, Object>> transactions = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    transactions.add(typed);
                }
            }
            return transactions;
        }

        log.warn("No credit card transactions returned for card {}", cardId);
        return List.of();
    }
}
