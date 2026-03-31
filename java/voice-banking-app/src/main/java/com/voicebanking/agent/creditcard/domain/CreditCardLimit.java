package com.voicebanking.agent.creditcard.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Credit card limit information.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCardLimit {
    private String cardId;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private BigDecimal currentBalance;
    private BigDecimal cashAdvanceLimit;
    private BigDecimal availableCashAdvance;
    private LocalDate lastLimitChange;
    private boolean increaseEligible;
    private BigDecimal maxIncreaseAmount;
    private String currency;

    private CreditCardLimit() {}

    public String getCardId() { return cardId; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getAvailableCredit() { return availableCredit; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getCashAdvanceLimit() { return cashAdvanceLimit; }
    public BigDecimal getAvailableCashAdvance() { return availableCashAdvance; }
    public LocalDate getLastLimitChange() { return lastLimitChange; }
    public boolean isIncreaseEligible() { return increaseEligible; }
    public BigDecimal getMaxIncreaseAmount() { return maxIncreaseAmount; }
    public String getCurrency() { return currency; }

    /**
     * Calculate credit utilization percentage.
     */
    public BigDecimal getUtilizationPercentage() {
        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentBalance.multiply(BigDecimal.valueOf(100))
                .divide(creditLimit, 1, RoundingMode.HALF_UP);
    }

    /**
     * Get utilization level category.
     */
    public UtilizationLevel getUtilizationLevel() {
        BigDecimal utilization = getUtilizationPercentage();
        if (utilization.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return UtilizationLevel.LOW;
        } else if (utilization.compareTo(BigDecimal.valueOf(70)) <= 0) {
            return UtilizationLevel.MODERATE;
        } else if (utilization.compareTo(BigDecimal.valueOf(90)) <= 0) {
            return UtilizationLevel.HIGH;
        } else {
            return UtilizationLevel.CRITICAL;
        }
    }

    /**
     * Format limit info for voice response.
     */
    public String formatForVoice(CreditCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Your %s has a credit limit of €%.0f. ", 
                card.getFriendlyName(), creditLimit.doubleValue()));
        sb.append(String.format("You currently have €%.0f available, ", 
                availableCredit.doubleValue()));
        sb.append(String.format("with a utilization of %.0f percent.", 
                getUtilizationPercentage().doubleValue()));
        
        if (increaseEligible && maxIncreaseAmount != null && 
                maxIncreaseAmount.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format(" You're eligible for a limit increase of up to €%.0f.", 
                    maxIncreaseAmount.doubleValue()));
        }
        
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cardId", cardId);
        map.put("creditLimit", creditLimit);
        map.put("availableCredit", availableCredit);
        map.put("currentBalance", currentBalance);
        map.put("cashAdvanceLimit", cashAdvanceLimit);
        map.put("availableCashAdvance", availableCashAdvance);
        map.put("lastLimitChange", lastLimitChange != null ? lastLimitChange.toString() : null);
        map.put("increaseEligible", increaseEligible);
        map.put("maxIncreaseAmount", maxIncreaseAmount);
        map.put("currency", currency);
        map.put("utilizationPercentage", getUtilizationPercentage());
        map.put("utilizationLevel", getUtilizationLevel().name());
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreditCardLimit limit = new CreditCardLimit();

        public Builder cardId(String s) { limit.cardId = s; return this; }
        public Builder creditLimit(BigDecimal b) { limit.creditLimit = b; return this; }
        public Builder availableCredit(BigDecimal b) { limit.availableCredit = b; return this; }
        public Builder currentBalance(BigDecimal b) { limit.currentBalance = b; return this; }
        public Builder cashAdvanceLimit(BigDecimal b) { limit.cashAdvanceLimit = b; return this; }
        public Builder availableCashAdvance(BigDecimal b) { limit.availableCashAdvance = b; return this; }
        public Builder lastLimitChange(LocalDate d) { limit.lastLimitChange = d; return this; }
        public Builder increaseEligible(boolean b) { limit.increaseEligible = b; return this; }
        public Builder maxIncreaseAmount(BigDecimal b) { limit.maxIncreaseAmount = b; return this; }
        public Builder currency(String s) { limit.currency = s; return this; }

        public CreditCardLimit build() { return limit; }
    }

    public enum UtilizationLevel {
        LOW, MODERATE, HIGH, CRITICAL
    }
}
