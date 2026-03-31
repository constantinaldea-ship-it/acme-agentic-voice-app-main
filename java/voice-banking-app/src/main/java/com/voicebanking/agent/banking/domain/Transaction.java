package com.voicebanking.agent.banking.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction Domain Model
 * 
 * <p>Represents a single financial transaction (debit or credit).</p>
 * 
 * @param id Unique transaction identifier
 * @param accountId Account identifier
 * @param date Transaction date (ISO 8601)
 * @param description Transaction description
 * @param amount Transaction amount (positive for credit, negative for debit by convention)
 * @param currency ISO 4217 currency code
 * @param type Transaction type (DEBIT or CREDIT)
 */
public record Transaction(
    @NotBlank(message = "Transaction ID is required")
    String id,
    
    @NotBlank(message = "Account ID is required")
    String accountId,
    
    @NotNull(message = "Transaction date is required")
    Instant date,
    
    @NotBlank(message = "Description is required")
    @Size(max = 200, message = "Description must not exceed 200 characters")
    String description,
    
    @NotNull(message = "Amount is required")
    BigDecimal amount,
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    String currency,
    
    @NotNull(message = "Transaction type is required")
    TransactionType type
) {
    /**
     * Transaction Type Enum
     */
    public enum TransactionType {
        DEBIT,
        CREDIT
    }
}
