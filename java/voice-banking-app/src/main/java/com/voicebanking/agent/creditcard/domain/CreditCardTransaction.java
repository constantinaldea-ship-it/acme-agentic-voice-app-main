package com.voicebanking.agent.creditcard.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Credit card transaction domain model.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCardTransaction {
    private String transactionId;
    private String cardId;
    private LocalDateTime transactionDate;
    private LocalDateTime postingDate;
    private BigDecimal amount;
    private String currency;
    private String merchantName;
    private String merchantCity;
    private String merchantCountry;
    private String mccCode;                    // Merchant Category Code
    private SpendingCategory category;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private BigDecimal rewardsEarned;

    private CreditCardTransaction() {}

    public String getTransactionId() { return transactionId; }
    public String getCardId() { return cardId; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public LocalDateTime getPostingDate() { return postingDate; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMerchantName() { return merchantName; }
    public String getMerchantCity() { return merchantCity; }
    public String getMerchantCountry() { return merchantCountry; }
    public String getMccCode() { return mccCode; }
    public SpendingCategory getCategory() { return category; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public BigDecimal getRewardsEarned() { return rewardsEarned; }

    public boolean isPurchase() {
        return type == TransactionType.PURCHASE || type == TransactionType.ONLINE_PURCHASE;
    }

    public boolean isCredit() {
        return type == TransactionType.PAYMENT || type == TransactionType.REFUND;
    }

    /**
     * Format transaction for voice response.
     */
    public String formatForVoice() {
        String prefix = isCredit() ? "Credit of" : "";
        String amountStr = String.format("€%.0f", Math.abs(amount.doubleValue()));
        String dateStr = formatDate();
        
        if (merchantName != null && !merchantName.isEmpty()) {
            return String.format("%s %s at %s on %s", prefix, amountStr, merchantName, dateStr).trim();
        } else {
            return String.format("%s %s on %s", prefix, amountStr, dateStr).trim();
        }
    }

    private String formatDate() {
        // Format as "January 15th" for voice
        String month = transactionDate.getMonth().name().substring(0, 1) + 
                       transactionDate.getMonth().name().substring(1).toLowerCase();
        int day = transactionDate.getDayOfMonth();
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
        map.put("transactionId", transactionId);
        map.put("cardId", cardId);
        map.put("transactionDate", transactionDate != null ? transactionDate.toString() : null);
        map.put("postingDate", postingDate != null ? postingDate.toString() : null);
        map.put("amount", amount);
        map.put("currency", currency);
        map.put("merchantName", merchantName);
        map.put("merchantCity", merchantCity);
        map.put("merchantCountry", merchantCountry);
        map.put("mccCode", mccCode);
        map.put("category", category != null ? category.name() : null);
        map.put("categoryDisplayName", category != null ? category.getDisplayName() : null);
        map.put("type", type != null ? type.name() : null);
        map.put("status", status != null ? status.name() : null);
        map.put("description", description);
        map.put("rewardsEarned", rewardsEarned);
        map.put("isPurchase", isPurchase());
        map.put("isCredit", isCredit());
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreditCardTransaction tx = new CreditCardTransaction();

        public Builder transactionId(String s) { tx.transactionId = s; return this; }
        public Builder cardId(String s) { tx.cardId = s; return this; }
        public Builder transactionDate(LocalDateTime d) { tx.transactionDate = d; return this; }
        public Builder postingDate(LocalDateTime d) { tx.postingDate = d; return this; }
        public Builder amount(BigDecimal b) { tx.amount = b; return this; }
        public Builder currency(String s) { tx.currency = s; return this; }
        public Builder merchantName(String s) { tx.merchantName = s; return this; }
        public Builder merchantCity(String s) { tx.merchantCity = s; return this; }
        public Builder merchantCountry(String s) { tx.merchantCountry = s; return this; }
        public Builder mccCode(String s) { tx.mccCode = s; return this; }
        public Builder category(SpendingCategory c) { tx.category = c; return this; }
        public Builder type(TransactionType t) { tx.type = t; return this; }
        public Builder status(TransactionStatus s) { tx.status = s; return this; }
        public Builder description(String s) { tx.description = s; return this; }
        public Builder rewardsEarned(BigDecimal b) { tx.rewardsEarned = b; return this; }

        public CreditCardTransaction build() { return tx; }
    }

    public enum TransactionType {
        PURCHASE, ONLINE_PURCHASE, PAYMENT, REFUND, CASH_ADVANCE, FEE, INTEREST
    }

    public enum TransactionStatus {
        PENDING, POSTED, REVERSED, DECLINED
    }
}
