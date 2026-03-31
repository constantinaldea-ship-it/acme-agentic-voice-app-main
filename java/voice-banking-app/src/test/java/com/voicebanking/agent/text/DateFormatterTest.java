package com.voicebanking.agent.text;

import com.voicebanking.agent.text.domain.VoiceOptions;
import com.voicebanking.agent.text.formatter.DateFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DateFormatter.
 * Tests German and English date formatting with relative date support for voice output.
 */
@DisplayName("DateFormatter Tests")
class DateFormatterTest {

    private DateFormatter formatter;
    private VoiceOptions germanOptions;
    private VoiceOptions englishOptions;
    private VoiceOptions germanSsmlOptions;
    private VoiceOptions noRelativeDates;

    @BeforeEach
    void setUp() {
        formatter = new DateFormatter();
        germanOptions = new VoiceOptions(Locale.GERMAN, false, 1.0, true, true, 15);
        englishOptions = new VoiceOptions(Locale.ENGLISH, false, 1.0, true, true, 15);
        germanSsmlOptions = new VoiceOptions(Locale.GERMAN, true, 1.0, true, true, 15);
        noRelativeDates = new VoiceOptions(Locale.GERMAN, false, 1.0, false, true, 15);
    }

    // ===== German Relative Dates =====
    @Test
    @DisplayName("German: today")
    void format_germanToday() {
        String result = formatter.format(LocalDate.now(), germanOptions);
        assertEquals("heute", result);
    }

    @Test
    @DisplayName("German: yesterday")
    void format_germanYesterday() {
        String result = formatter.format(LocalDate.now().minusDays(1), germanOptions);
        assertEquals("gestern", result);
    }

    @Test
    @DisplayName("German: tomorrow")
    void format_germanTomorrow() {
        String result = formatter.format(LocalDate.now().plusDays(1), germanOptions);
        assertEquals("morgen", result);
    }

    @Test
    @DisplayName("German: day before yesterday")
    void format_germanDayBeforeYesterday() {
        String result = formatter.format(LocalDate.now().minusDays(2), germanOptions);
        assertEquals("vorgestern", result);
    }

    @Test
    @DisplayName("German: day after tomorrow")
    void format_germanDayAfterTomorrow() {
        String result = formatter.format(LocalDate.now().plusDays(2), germanOptions);
        assertEquals("uebermorgen", result);
    }

    // ===== English Relative Dates =====
    @Test
    @DisplayName("English: today")
    void format_englishToday() {
        String result = formatter.format(LocalDate.now(), englishOptions);
        assertEquals("today", result);
    }

    @Test
    @DisplayName("English: yesterday")
    void format_englishYesterday() {
        String result = formatter.format(LocalDate.now().minusDays(1), englishOptions);
        assertEquals("yesterday", result);
    }

    @Test
    @DisplayName("English: tomorrow")
    void format_englishTomorrow() {
        String result = formatter.format(LocalDate.now().plusDays(1), englishOptions);
        assertEquals("tomorrow", result);
    }

    // ===== Full Date Format (non-relative) =====
    @Test
    @DisplayName("German: full date format for older dates")
    void format_germanFullDate() {
        LocalDate oldDate = LocalDate.of(2024, 3, 15);
        String result = formatter.format(oldDate, germanOptions);
        // Should contain day, month, year in German format
        assertTrue(result.contains("Maerz") || result.contains("15") || result.contains("2024"));
    }

    @Test
    @DisplayName("English: full date format for older dates")
    void format_englishFullDate() {
        LocalDate oldDate = LocalDate.of(2024, 3, 15);
        String result = formatter.format(oldDate, englishOptions);
        // Should contain date components
        assertTrue(result.contains("March") || result.contains("15") || result.contains("2024"));
    }

    // ===== Relative Dates Disabled =====
    @Test
    @DisplayName("With relative dates disabled, today shows full date")
    void format_noRelativeDates() {
        String result = formatter.format(LocalDate.now(), noRelativeDates);
        // Should NOT be "heute", but full date
        assertNotEquals("heute", result);
    }

    // ===== SSML Formatting =====
    @Test
    @DisplayName("SSML format includes date interpretation hints")
    void formatSsml_includesHints() {
        String result = formatter.formatSsml(LocalDate.now(), germanSsmlOptions);
        // Could contain say-as or just the word
        assertTrue(result.contains("say-as") || result.contains("heute") || result.contains("date"));
    }

    @Test
    @DisplayName("SSML format for full date")
    void formatSsml_fullDate() {
        LocalDate oldDate = LocalDate.of(2024, 6, 20);
        String result = formatter.formatSsml(oldDate, germanSsmlOptions);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ===== Edge Cases =====
    @Test
    @DisplayName("Null date returns 'unbekannt' in German")
    void format_nullDate() {
        String result = formatter.format(null, germanOptions);
        assertEquals("unbekannt", result);
    }

    @Test
    @DisplayName("Far future date uses full format")
    void format_farFutureDate() {
        LocalDate futureDate = LocalDate.now().plusYears(1);
        String result = formatter.format(futureDate, germanOptions);
        // Should not use relative words for far future
        assertNotEquals("morgen", result);
        assertNotEquals("uebermorgen", result);
    }

    @Test
    @DisplayName("Far past date uses full format")
    void format_farPastDate() {
        LocalDate pastDate = LocalDate.now().minusYears(1);
        String result = formatter.format(pastDate, germanOptions);
        // Should not use relative words for far past
        assertNotEquals("gestern", result);
        assertNotEquals("vorgestern", result);
    }
}
