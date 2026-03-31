package com.voicebanking.agent.text.formatter;

import com.voicebanking.agent.text.domain.VoiceOptions;
import org.springframework.stereotype.Component;

/**
 * Formats account numbers for voice output with masking.
 * Reads digits individually for clarity.
 */
@Component
public class AccountFormatter implements VoiceFormatter<String> {

    private static final int VISIBLE_DIGITS = 4;

    @Override
    public String format(String accountNumber, VoiceOptions options) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return options.locale().getLanguage().equals("de") ? "unbekannt" : "unknown";
        }

        // Remove non-digits for processing
        String digits = accountNumber.replaceAll("[^0-9]", "");

        // Mask all but last 4 digits
        String masked = maskAccount(digits);

        // Format for voice - read digit by digit with pauses
        boolean isGerman = options.locale().getLanguage().equals("de");
        return formatForVoice(masked, isGerman);
    }

    @Override
    public String formatSsml(String accountNumber, VoiceOptions options) {
        if (options.ssmlEnabled() == false) {
            return format(accountNumber, options);
        }

        String digits = accountNumber.replaceAll("[^0-9]", "");
        String masked = maskAccount(digits);

        StringBuilder sb = new StringBuilder();
        for (char c : masked.toCharArray()) {
            if (c == X_CHAR) {
                sb.append("<say-as interpret-as=\"characters\">X</say-as><break time=\"100ms\"/>");
            } else {
                sb.append("<say-as interpret-as=\"characters\">").append(c).append("</say-as><break time=\"100ms\"/>");
            }
        }
        return sb.toString();
    }

    private static final char X_CHAR = 'X';

    private String maskAccount(String digits) {
        if (digits.length() <= VISIBLE_DIGITS) {
            return digits;
        }
        int maskedLength = digits.length() - VISIBLE_DIGITS;
        String masked = "X".repeat(maskedLength);
        return masked + digits.substring(maskedLength);
    }

    private String formatForVoice(String masked, boolean isGerman) {
        StringBuilder sb = new StringBuilder();
        String[] germanDigits = {"null", "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun"};
        String[] englishDigits = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        String[] digits = isGerman ? germanDigits : englishDigits;

        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == X_CHAR) {
                sb.append("X");
            } else {
                sb.append(digits[c - '0']);
            }
            if (i < masked.length() - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
