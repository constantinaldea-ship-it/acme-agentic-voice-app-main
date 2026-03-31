package com.voicebanking.agent.banking.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Account Balance Domain Model
 * 
 * <p>Represents the current balance of a bank account at a specific point in time.</p>
 * 
 * @param accountId Account identifier
 * @param available Available balance (can be spent/withdrawn)
 * @param current Current balance (includes pending transactions)
 * @param currency ISO 4217 currency code
 * @param asOf Timestamp when balance was calculated (ISO 8601)
 */
public record Balance(
    @NotBlank(message = "Account ID is required")
    String accountId,
    
    @NotNull(message = "Available balance is required")
    BigDecimal available,
    
    @NotNull(message = "Current balance is required")
    BigDecimal current,
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    String currency,
    
    @NotNull(message = "As-of timestamp is required")
    Instant asOf
) {
}
