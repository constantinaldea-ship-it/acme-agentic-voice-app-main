package com.voicebanking.agent.text.formatter;

import com.voicebanking.agent.text.domain.VoiceOptions;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Locale;

/**
 * Formats dates for voice output with relative date support.
 */
@Component
public class DateFormatter implements VoiceFormatter<Temporal> {

    private static final DateTimeFormatter GERMAN_FULL = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN);
    private static final DateTimeFormatter ENGLISH_FULL = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    @Override
    public String format(Temporal value, VoiceOptions options) {
        if (value == null) {
            return options.locale().getLanguage().equals("de") ? "unbekannt" : "unknown";
        }

        LocalDate date = toLocalDate(value);
        boolean isGerman = options.locale().getLanguage().equals("de");

        if (options.useRelativeDates()) {
            String relative = formatRelative(date, isGerman);
            if (relative != null) return relative;
        }

        DateTimeFormatter formatter = isGerman ? GERMAN_FULL : ENGLISH_FULL;
        return date.format(formatter);
    }

    @Override
    public String formatSsml(Temporal value, VoiceOptions options) {
        String text = format(value, options);
        if (options.ssmlEnabled() == false) {
            return text;
        }
        return "<say-as interpret-as=\"date\">" + text + "</say-as>";
    }

    private LocalDate toLocalDate(Temporal temporal) {
        if (temporal instanceof LocalDate) {
            return (LocalDate) temporal;
        } else if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).toLocalDate();
        }
        return LocalDate.from(temporal);
    }

    private String formatRelative(LocalDate date, boolean isGerman) {
        LocalDate today = LocalDate.now();
        long daysBetween = ChronoUnit.DAYS.between(today, date);

        if (daysBetween == 0) return isGerman ? "heute" : "today";
        if (daysBetween == 1) return isGerman ? "morgen" : "tomorrow";
        if (daysBetween == -1) return isGerman ? "gestern" : "yesterday";
        if (daysBetween == 2) return isGerman ? "uebermorgen" : "day after tomorrow";
        if (daysBetween == -2) return isGerman ? "vorgestern" : "day before yesterday";

        // For dates within 7 days, use day name
        if (daysBetween > 0 && daysBetween < 7) {
            String dayName = date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, isGerman ? Locale.GERMAN : Locale.ENGLISH);
            return isGerman ? "am " + dayName : "on " + dayName;
        }

        // Return null to fall back to full format
        return null;
    }
}
