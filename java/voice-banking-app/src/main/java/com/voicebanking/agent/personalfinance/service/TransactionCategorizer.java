package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.banking.domain.Transaction;
import com.voicebanking.agent.personalfinance.domain.SpendingCategory;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Categorizes transactions into PFM categories.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class TransactionCategorizer {
    public SpendingCategory categorize(Transaction transaction) {
        if (transaction == null || transaction.description() == null) {
            return SpendingCategory.OTHER;
        }
        String text = transaction.description().toLowerCase(Locale.ROOT);

        if (containsAny(text, "rent", "mortgage", "utility", "electric", "e.on", "water")) {
            return SpendingCategory.HOUSING;
        }
        if (containsAny(text, "grocery", "rewe", "edeka", "aldi", "lidl", "restaurant", "starbucks", "cafe")) {
            return SpendingCategory.FOOD;
        }
        if (containsAny(text, "transport", "uber", "bahn", "mvv", "shell", "gas", "parking")) {
            return SpendingCategory.TRANSPORT;
        }
        if (containsAny(text, "amazon", "zalando", "mediamarkt", "shopping", "ikea")) {
            return SpendingCategory.SHOPPING;
        }
        if (containsAny(text, "netflix", "spotify", "ticketmaster", "cinema", "entertainment")) {
            return SpendingCategory.ENTERTAINMENT;
        }
        if (containsAny(text, "pharmacy", "apotheke", "health", "fitness", "insurance")) {
            return SpendingCategory.HEALTH;
        }
        if (containsAny(text, "fee", "transfer", "interest", "payment", "deposit")) {
            return SpendingCategory.FINANCIAL;
        }

        return SpendingCategory.OTHER;
    }

    public SpendingCategory mapCreditCardCategory(String categoryValue) {
        if (categoryValue == null) {
            return SpendingCategory.OTHER;
        }
        return switch (categoryValue.toUpperCase(Locale.ROOT)) {
            case "RESTAURANTS", "GROCERIES" -> SpendingCategory.FOOD;
            case "TRANSPORT" -> SpendingCategory.TRANSPORT;
            case "SHOPPING" -> SpendingCategory.SHOPPING;
            case "ENTERTAINMENT" -> SpendingCategory.ENTERTAINMENT;
            case "TRAVEL" -> SpendingCategory.TRANSPORT;
            case "UTILITIES", "INSURANCE" -> SpendingCategory.HOUSING;
            case "HEALTH" -> SpendingCategory.HEALTH;
            default -> SpendingCategory.OTHER;
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
