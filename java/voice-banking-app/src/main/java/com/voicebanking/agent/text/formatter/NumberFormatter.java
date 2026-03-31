package com.voicebanking.agent.text.formatter;

import com.voicebanking.agent.text.domain.VoiceOptions;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;

/**
 * Formats numbers for voice output with German and English support.
 * Includes number-to-words conversion for natural speech.
 */
@Component
public class NumberFormatter implements VoiceFormatter<Number> {

    private static final String[] GERMAN_ONES = {
        "", "eins", "zwei", "drei", "vier", "fuenf", "sechs", "sieben", "acht", "neun",
        "zehn", "elf", "zwoelf", "dreizehn", "vierzehn", "fuenfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"
    };

    private static final String[] GERMAN_TENS = {
        "", "", "zwanzig", "dreissig", "vierzig", "fuenfzig", "sechzig", "siebzig", "achtzig", "neunzig"
    };

    private static final String[] ENGLISH_ONES = {
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    };

    private static final String[] ENGLISH_TENS = {
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    @Override
    public String format(Number value, VoiceOptions options) {
        if (value == null) {
            return options.locale().getLanguage().equals("de") ? "null" : "zero";
        }

        if (options.conversationalNumbers()) {
            boolean isGerman = options.locale().getLanguage().equals("de");
            return isGerman ? toGermanWords(value.longValue()) : toEnglishWords(value.longValue());
        }

        return NumberFormat.getInstance(options.locale()).format(value);
    }

    @Override
    public String formatSsml(Number value, VoiceOptions options) {
        String text = format(value, options);
        if (options.ssmlEnabled() == false) {
            return text;
        }
        return "<say-as interpret-as=\"cardinal\">" + text + "</say-as>";
    }

    /**
     * Convert number to German words.
     * Handles compound numbers like "einhundertdreiundzwanzig".
     */
    public String toGermanWords(long number) {
        if (number == 0) return "null";
        if (number < 0) return "minus " + toGermanWords(-number);
        if (number < 20) return GERMAN_ONES[(int) number];

        if (number < 100) {
            int tens = (int) (number / 10);
            int ones = (int) (number % 10);
            if (ones == 0) return GERMAN_TENS[tens];
            // German pattern: ones-und-tens (e.g., einundzwanzig)
            String onesWord = ones == 1 ? "ein" : GERMAN_ONES[ones];
            return onesWord + "und" + GERMAN_TENS[tens];
        }

        if (number < 1000) {
            int hundreds = (int) (number / 100);
            long remainder = number % 100;
            String hundredsWord = (hundreds == 1 ? "ein" : GERMAN_ONES[hundreds]) + "hundert";
            if (remainder == 0) return hundredsWord;
            return hundredsWord + toGermanWords(remainder);
        }

        if (number < 1000000) {
            int thousands = (int) (number / 1000);
            long remainder = number % 1000;
            String thousandsWord = (thousands == 1 ? "ein" : toGermanWords(thousands)) + "tausend";
            if (remainder == 0) return thousandsWord;
            return thousandsWord + toGermanWords(remainder);
        }

        // Millions and above - add spaces for readability
        if (number < 1000000000) {
            int millions = (int) (number / 1000000);
            long remainder = number % 1000000;
            String millionsWord = (millions == 1 ? "eine Million" : toGermanWords(millions) + " Millionen");
            if (remainder == 0) return millionsWord;
            return millionsWord + " " + toGermanWords(remainder);
        }

        // For very large numbers, fall back to standard format
        return String.valueOf(number);
    }

    /**
     * Convert number to English words.
     * Handles numbers up to billions.
     */
    public String toEnglishWords(long number) {
        if (number == 0) return "zero";
        if (number < 0) return "minus " + toEnglishWords(-number);
        if (number < 20) return ENGLISH_ONES[(int) number];

        if (number < 100) {
            int tens = (int) (number / 10);
            int ones = (int) (number % 10);
            if (ones == 0) return ENGLISH_TENS[tens];
            return ENGLISH_TENS[tens] + "-" + ENGLISH_ONES[ones];
        }

        if (number < 1000) {
            int hundreds = (int) (number / 100);
            long remainder = number % 100;
            String hundredsWord = ENGLISH_ONES[hundreds] + " hundred";
            if (remainder == 0) return hundredsWord;
            return hundredsWord + " " + toEnglishWords(remainder);
        }

        if (number < 1000000) {
            int thousands = (int) (number / 1000);
            long remainder = number % 1000;
            String thousandsWord = toEnglishWords(thousands) + " thousand";
            if (remainder == 0) return thousandsWord;
            return thousandsWord + " " + toEnglishWords(remainder);
        }

        if (number < 1000000000) {
            int millions = (int) (number / 1000000);
            long remainder = number % 1000000;
            String millionsWord = toEnglishWords(millions) + " million";
            if (remainder == 0) return millionsWord;
            return millionsWord + " " + toEnglishWords(remainder);
        }

        if (number < 1000000000000L) {
            int billions = (int) (number / 1000000000);
            long remainder = number % 1000000000;
            String billionsWord = toEnglishWords(billions) + " billion";
            if (remainder == 0) return billionsWord;
            return billionsWord + " " + toEnglishWords(remainder);
        }

        // For very large numbers, fall back to standard format
        return String.valueOf(number);
    }
}
