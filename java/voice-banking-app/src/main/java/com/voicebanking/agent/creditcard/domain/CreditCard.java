package com.voicebanking.agent.creditcard.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Credit card domain model with secure handling of card numbers.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCard {
    private String cardId;
    private String customerId;
    private String cardType;         // GOLD, PLATINUM, STANDARD
    private String cardNetwork;      // VISA, MASTERCARD
    private String maskedCardNumber; // ****-****-****-1234
    private String cardholderName;
    private LocalDate expiryDate;
    private CardStatus status;
    private String rewardsProgramId;
    private LocalDate activationDate;

    private CreditCard() {}

    public String getCardId() { return cardId; }
    public String getCustomerId() { return customerId; }
    public String getCardType() { return cardType; }
    public String getCardNetwork() { return cardNetwork; }
    public String getMaskedCardNumber() { return maskedCardNumber; }
    public String getCardholderName() { return cardholderName; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public CardStatus getStatus() { return status; }
    public String getRewardsProgramId() { return rewardsProgramId; }
    public LocalDate getActivationDate() { return activationDate; }

    public boolean isActive() {
        return status == CardStatus.ACTIVE;
    }

    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    /**
     * Get last 4 digits of card number for display.
     */
    public String getLastFourDigits() {
        if (maskedCardNumber == null || maskedCardNumber.length() < 4) {
            return "****";
        }
        return maskedCardNumber.substring(maskedCardNumber.length() - 4);
    }

    /**
     * Get friendly card name for voice responses.
     * Example: "Gold MasterCard ending in 4821"
     */
    public String getFriendlyName() {
        return String.format("%s %s ending in %s", cardType, cardNetwork, getLastFourDigits());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("cardId", cardId);
        map.put("customerId", customerId);
        map.put("cardType", cardType);
        map.put("cardNetwork", cardNetwork);
        map.put("maskedCardNumber", maskedCardNumber);
        map.put("cardholderName", cardholderName);
        map.put("expiryDate", expiryDate != null ? expiryDate.toString() : null);
        map.put("status", status != null ? status.name() : null);
        map.put("rewardsProgramId", rewardsProgramId);
        map.put("friendlyName", getFriendlyName());
        map.put("isActive", isActive());
        return map;
    }

    /**
     * Mask a full card number for security.
     * Input: 4532123456781234 -> Output: ****-****-****-1234
     */
    public static String maskCardNumber(String fullCardNumber) {
        if (fullCardNumber == null || fullCardNumber.length() < 4) {
            return "****-****-****-****";
        }
        String digits = fullCardNumber.replaceAll("[^0-9]", "");
        String lastFour = digits.length() >= 4 
            ? digits.substring(digits.length() - 4) 
            : digits;
        return "****-****-****-" + lastFour;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreditCard card = new CreditCard();

        public Builder cardId(String s) { card.cardId = s; return this; }
        public Builder customerId(String s) { card.customerId = s; return this; }
        public Builder cardType(String s) { card.cardType = s; return this; }
        public Builder cardNetwork(String s) { card.cardNetwork = s; return this; }
        public Builder maskedCardNumber(String s) { card.maskedCardNumber = s; return this; }
        public Builder cardholderName(String s) { card.cardholderName = s; return this; }
        public Builder expiryDate(LocalDate d) { card.expiryDate = d; return this; }
        public Builder status(CardStatus s) { card.status = s; return this; }
        public Builder rewardsProgramId(String s) { card.rewardsProgramId = s; return this; }
        public Builder activationDate(LocalDate d) { card.activationDate = d; return this; }

        public CreditCard build() { return card; }
    }

    public enum CardStatus {
        ACTIVE, BLOCKED, EXPIRED, PENDING_ACTIVATION, CANCELLED
    }
}
