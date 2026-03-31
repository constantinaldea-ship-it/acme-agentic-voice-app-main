package com.voicebanking.agent.creditcard.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Credit card rewards balance and history.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCardRewards {
    private String cardId;
    private String programName;              // e.g., "Miles & More", "Cashback"
    private RewardsType rewardsType;
    private BigDecimal currentBalance;       // Points or cashback amount
    private BigDecimal pendingRewards;       // Not yet credited
    private BigDecimal earnedThisMonth;
    private BigDecimal earnedThisYear;
    private BigDecimal redeemedThisYear;
    private LocalDate expiryDate;            // For points that expire
    private BigDecimal expiringAmount;       // Amount expiring soon
    private List<RedemptionOption> redemptionOptions = new ArrayList<>();
    private LocalDate lastUpdated;

    private CreditCardRewards() {}

    public String getCardId() { return cardId; }
    public String getProgramName() { return programName; }
    public RewardsType getRewardsType() { return rewardsType; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getPendingRewards() { return pendingRewards; }
    public BigDecimal getEarnedThisMonth() { return earnedThisMonth; }
    public BigDecimal getEarnedThisYear() { return earnedThisYear; }
    public BigDecimal getRedeemedThisYear() { return redeemedThisYear; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public BigDecimal getExpiringAmount() { return expiringAmount; }
    public List<RedemptionOption> getRedemptionOptions() { return List.copyOf(redemptionOptions); }
    public LocalDate getLastUpdated() { return lastUpdated; }

    /**
     * Check if rewards are expiring soon (within 90 days).
     */
    public boolean hasExpiringRewards() {
        return expiryDate != null && 
               expiryDate.isBefore(LocalDate.now().plusDays(90)) &&
               expiringAmount != null && 
               expiringAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Format rewards for voice response.
     */
    public String formatForVoice(CreditCard card) {
        StringBuilder sb = new StringBuilder();
        
        if (rewardsType == RewardsType.POINTS) {
            sb.append(String.format("You have %.0f %s points on your %s. ", 
                    currentBalance.doubleValue(), programName, card.getFriendlyName()));
            sb.append(String.format("You earned %.0f points this month. ", 
                    earnedThisMonth.doubleValue()));
        } else {
            sb.append(String.format("You have €%.2f cashback on your %s. ", 
                    currentBalance.doubleValue(), card.getFriendlyName()));
            sb.append(String.format("You earned €%.2f this month. ", 
                    earnedThisMonth.doubleValue()));
        }
        
        if (hasExpiringRewards()) {
            String amountStr = rewardsType == RewardsType.POINTS 
                    ? String.format("%.0f points", expiringAmount.doubleValue())
                    : String.format("€%.2f", expiringAmount.doubleValue());
            sb.append(String.format("Note: %s will expire on %s. ", 
                    amountStr, formatDate(expiryDate)));
        }
        
        if (!redemptionOptions.isEmpty()) {
            sb.append("Would you like to know about redemption options?");
        }
        
        return sb.toString();
    }

    private String formatDate(LocalDate date) {
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
        map.put("programName", programName);
        map.put("rewardsType", rewardsType != null ? rewardsType.name() : null);
        map.put("currentBalance", currentBalance);
        map.put("pendingRewards", pendingRewards);
        map.put("earnedThisMonth", earnedThisMonth);
        map.put("earnedThisYear", earnedThisYear);
        map.put("redeemedThisYear", redeemedThisYear);
        map.put("expiryDate", expiryDate != null ? expiryDate.toString() : null);
        map.put("expiringAmount", expiringAmount);
        map.put("hasExpiringRewards", hasExpiringRewards());
        map.put("lastUpdated", lastUpdated != null ? lastUpdated.toString() : null);
        map.put("redemptionOptions", redemptionOptions.stream()
                .map(RedemptionOption::toMap)
                .collect(Collectors.toList()));
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreditCardRewards rewards = new CreditCardRewards();

        public Builder cardId(String s) { rewards.cardId = s; return this; }
        public Builder programName(String s) { rewards.programName = s; return this; }
        public Builder rewardsType(RewardsType t) { rewards.rewardsType = t; return this; }
        public Builder currentBalance(BigDecimal b) { rewards.currentBalance = b; return this; }
        public Builder pendingRewards(BigDecimal b) { rewards.pendingRewards = b; return this; }
        public Builder earnedThisMonth(BigDecimal b) { rewards.earnedThisMonth = b; return this; }
        public Builder earnedThisYear(BigDecimal b) { rewards.earnedThisYear = b; return this; }
        public Builder redeemedThisYear(BigDecimal b) { rewards.redeemedThisYear = b; return this; }
        public Builder expiryDate(LocalDate d) { rewards.expiryDate = d; return this; }
        public Builder expiringAmount(BigDecimal b) { rewards.expiringAmount = b; return this; }
        public Builder redemptionOptions(List<RedemptionOption> l) { 
            rewards.redemptionOptions = new ArrayList<>(l); 
            return this; 
        }
        public Builder addRedemptionOption(RedemptionOption o) { 
            rewards.redemptionOptions.add(o); 
            return this; 
        }
        public Builder lastUpdated(LocalDate d) { rewards.lastUpdated = d; return this; }

        public CreditCardRewards build() { return rewards; }
    }

    public enum RewardsType {
        POINTS, CASHBACK, MILES
    }

    public record RedemptionOption(
            String optionId,
            String name,
            String description,
            BigDecimal pointsRequired,
            BigDecimal cashValue
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "optionId", optionId,
                    "name", name,
                    "description", description,
                    "pointsRequired", pointsRequired,
                    "cashValue", cashValue
            );
        }
    }
}
