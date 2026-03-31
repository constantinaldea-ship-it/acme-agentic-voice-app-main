package com.voicebanking.agent.creditcard.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Credit card balance information.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCardBalance {
    private String cardId;
    private BigDecimal currentBalance;      // Amount owed
    private BigDecimal availableCredit;     // Credit remaining
    private BigDecimal creditLimit;
    private BigDecimal minimumPayment;
    private LocalDate paymentDueDate;
    private LocalDate lastStatementDate;
    private BigDecimal lastPaymentAmount;
    private LocalDate lastPaymentDate;
    private String currency;

    private CreditCardBalance() {}

    public String getCardId() { return cardId; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getAvailableCredit() { return availableCredit; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getMinimumPayment() { return minimumPayment; }
    public LocalDate getPaymentDueDate() { return paymentDueDate; }
    public LocalDate getLastStatementDate() { return lastStatementDate; }
    public BigDecimal getLastPaymentAmount() { return lastPaymentAmount; }
    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
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
     * Check if payment is due soon (within 7 days).
     */
    public boolean isPaymentDueSoon() {
        if (paymentDueDate == null) return false;
        return paymentDueDate.isBefore(LocalDate.now().plusDays(7)) &&
               !paymentDueDate.isBefore(LocalDate.now());
    }

    /**
     * Check if payment is overdue.
     */
    public boolean isOverdue() {
        if (paymentDueDate == null) return false;
        return paymentDueDate.isBefore(LocalDate.now()) && 
               currentBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Format balance for voice response.
     */
    public String formatForVoice(CreditCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Your %s has a current balance of €%.0f.", 
                card.getFriendlyName(), currentBalance.doubleValue()));
        sb.append(String.format(" Your available credit is €%.0f.", 
                availableCredit.doubleValue()));
        
        if (minimumPayment != null && minimumPayment.compareTo(BigDecimal.ZERO) > 0 
                && paymentDueDate != null) {
            sb.append(String.format(" Your minimum payment of €%.0f is due on %s.", 
                    minimumPayment.doubleValue(), formatDate(paymentDueDate)));
        }
        
        return sb.toString();
    }

    private String formatDate(LocalDate date) {
        // Format as "January 15th" for voice
        String month = date.getMonth().name().substring(0, 1) + 
                       date.getMonth().name().substring(1).toLowerCase();
        int day = date.getDayOfMonth();
        String suffix = getDaySuffix(day);
        return month + " " + day + suffix;
    }

    private String getDaySuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cardId", cardId);
        map.put("currentBalance", currentBalance);
        map.put("availableCredit", availableCredit);
        map.put("creditLimit", creditLimit);
        map.put("minimumPayment", minimumPayment);
        map.put("paymentDueDate", paymentDueDate != null ? paymentDueDate.toString() : null);
        map.put("lastStatementDate", lastStatementDate != null ? lastStatementDate.toString() : null);
        map.put("lastPaymentAmount", lastPaymentAmount);
        map.put("lastPaymentDate", lastPaymentDate != null ? lastPaymentDate.toString() : null);
        map.put("currency", currency);
        map.put("utilizationPercentage", getUtilizationPercentage());
        map.put("isOverdue", isOverdue());
        map.put("isPaymentDueSoon", isPaymentDueSoon());
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreditCardBalance balance = new CreditCardBalance();

        public Builder cardId(String s) { balance.cardId = s; return this; }
        public Builder currentBalance(BigDecimal b) { balance.currentBalance = b; return this; }
        public Builder availableCredit(BigDecimal b) { balance.availableCredit = b; return this; }
        public Builder creditLimit(BigDecimal b) { balance.creditLimit = b; return this; }
        public Builder minimumPayment(BigDecimal b) { balance.minimumPayment = b; return this; }
        public Builder paymentDueDate(LocalDate d) { balance.paymentDueDate = d; return this; }
        public Builder lastStatementDate(LocalDate d) { balance.lastStatementDate = d; return this; }
        public Builder lastPaymentAmount(BigDecimal b) { balance.lastPaymentAmount = b; return this; }
        public Builder lastPaymentDate(LocalDate d) { balance.lastPaymentDate = d; return this; }
        public Builder currency(String s) { balance.currency = s; return this; }

        public CreditCardBalance build() { return balance; }
    }
}
