package com.voicebanking.agent.banking.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bank Account Domain Model
 * 
 * <p>Represents a customer bank account (checking, savings, or card).
 * Immutable record with Bean Validation constraints.</p>
 * 
 * @param id Unique account identifier
 * @param type Account type (CHECKING, SAVINGS, CARD)
 * @param name Display name
 * @param currency ISO 4217 currency code (e.g., "USD", "EUR")
 * @param lastFour Last 4 digits of account number
 */
public record Account(
    @NotBlank(message = "Account ID is required")
    String id,
    
    @NotNull(message = "Account type is required")
    AccountType type,
    
    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name must not exceed 100 characters")
    String name,
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    String currency,
    
    @NotBlank(message = "Last four digits are required")
    @Size(min = 4, max = 4, message = "Last four must be exactly 4 characters")
    String lastFour
) {
    /**
     * Account Type Enum
     */
    public enum AccountType {
        CHECKING,
        SAVINGS,
        CARD
    }
}
