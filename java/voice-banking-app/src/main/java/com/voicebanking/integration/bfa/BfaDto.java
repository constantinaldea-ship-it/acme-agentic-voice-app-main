package com.voicebanking.integration.bfa;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTOs for BFA Resource-Oriented API responses.
 * 
 * <p>These DTOs mirror the bfa-service-resource API contracts and provide
 * type-safe access to banking data.</p>
 *
 * @author Augment Agent
 * @since 2026-02-02
 */
public final class BfaDto {
    
    private BfaDto() {} // Utility class
    
    /**
     * Wrapper response for all BFA API calls.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorInfo error,
        Meta meta
    ) {
        public record ErrorInfo(String code, String message, Object details) {}
        public record Meta(String correlationId, Instant timestamp, String apiVersion, Pagination pagination) {}
        public record Pagination(int page, int size, long totalElements, int totalPages) {}
    }
    
    /**
     * Bank account information.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountDto(
        String id,
        AccountType type,
        String name,
        String currency,
        String lastFour,
        String maskedIban
    ) {
        public enum AccountType {
            CHECKING, SAVINGS, MONEY_MARKET, CERTIFICATE_OF_DEPOSIT
        }
    }
    
    /**
     * Account or card balance information.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BalanceDto(
        String resourceId,
        String resourceType,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        String currency,
        BigDecimal creditLimit,
        BigDecimal minimumPayment,
        Instant nextPaymentDue,
        Instant lastUpdated
    ) {}
    
    /**
     * Financial transaction.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionDto(
        String id,
        String resourceId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        String merchantName,
        String merchantCategoryCode,
        String category,
        Instant timestamp,
        Instant bookingDate,
        Instant valueDate,
        TransactionStatus status,
        String reference
    ) {
        public enum TransactionType { DEBIT, CREDIT }
        public enum TransactionStatus { PENDING, COMPLETED, FAILED, CANCELLED }
    }
    
    /**
     * Credit card information.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CardDto(
        String id,
        CardType type,
        String name,
        String lastFour,
        String cardholderName,
        String expirationMonth,
        BigDecimal creditLimit,
        String currency,
        CardStatus status,
        Instant nextPaymentDue,
        BigDecimal minimumPayment
    ) {
        public enum CardType { 
            VISA_PLATINUM, VISA_GOLD, VISA_CLASSIC,
            MASTERCARD_PLATINUM, MASTERCARD_GOLD, MASTERCARD_CLASSIC,
            AMEX_PLATINUM, AMEX_GOLD
        }
        public enum CardStatus { ACTIVE, BLOCKED, EXPIRED, PENDING_ACTIVATION }
    }
    
    /**
     * Transaction query parameters.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionQuery(
        Instant dateFrom,
        Instant dateTo,
        BigDecimal amountMin,
        BigDecimal amountMax,
        String category,
        String searchTerm,
        Integer limit,
        Integer offset,
        String sortBy,
        String sortOrder
    ) {
        public static TransactionQuery ofDateRange(Instant from, Instant to, Integer limit) {
            return new TransactionQuery(from, to, null, null, null, null, limit, null, null, null);
        }
    }
}
