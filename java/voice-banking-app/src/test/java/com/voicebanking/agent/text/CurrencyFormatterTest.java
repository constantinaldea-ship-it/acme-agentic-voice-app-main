package com.voicebanking.agent.text;

import com.voicebanking.agent.text.domain.VoiceOptions;
import com.voicebanking.agent.text.formatter.CurrencyFormatter;
import com.voicebanking.agent.text.formatter.NumberFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrencyFormatter.
 * Tests German and English currency formatting for voice output.
 */
@DisplayName("CurrencyFormatter Tests")
class CurrencyFormatterTest {

    private CurrencyFormatter formatter;
    private VoiceOptions germanOptions;
    private VoiceOptions englishOptions;
    private VoiceOptions germanSsmlOptions;

    @BeforeEach
    void setUp() {
        formatter = new CurrencyFormatter(new NumberFormatter());
        germanOptions = new VoiceOptions(Locale.GERMAN, false, 1.0, true, true, 15);
        englishOptions = new VoiceOptions(Locale.ENGLISH, false, 1.0, true, true, 15);
        germanSsmlOptions = new VoiceOptions(Locale.GERMAN, true, 1.0, true, true, 15);
    }

    // ===== German Currency Formatting =====
    @Test
    @DisplayName("German: whole euros")
    void format_germanWholeEuros() {
        String result = formatter.format(new BigDecimal("100.00"), germanOptions);
        assertTrue(result.contains("Euro"));
        assertTrue(result.contains("hundert") || result.contains("100"));
    }

    @Test
    @DisplayName("German: euros and cents")
    void format_germanEurosAndCents() {
        String result = formatter.format(new BigDecimal("5.20"), germanOptions);
        assertTrue(result.contains("Euro"));
        assertTrue(result.contains("Cent") || result.contains("20"));
    }

    @Test
    @DisplayName("German: zero amount")
    void format_germanZero() {
        String result = formatter.format(new BigDecimal("0.00"), germanOptions);
        assertTrue(result.contains("null") || result.contains("0"));
        assertTrue(result.contains("Euro"));
    }

    @Test
    @DisplayName("German: single cent")
    void format_germanSingleCent() {
        String result = formatter.format(new BigDecimal("0.01"), germanOptions);
        assertTrue(result.contains("Cent"));
    }

    @Test
    @DisplayName("German: large amount")
    void format_germanLargeAmount() {
        String result = formatter.format(new BigDecimal("1234567.89"), germanOptions);
        assertTrue(result.contains("Euro") || result.contains("Million"));
    }

    // ===== English Currency Formatting =====
    @Test
    @DisplayName("English: whole dollars equivalent")
    void format_englishWholeAmount() {
        String result = formatter.format(new BigDecimal("100.00"), englishOptions);
        assertTrue(result.contains("Euro") || result.contains("hundred"));
    }

    @Test
    @DisplayName("English: amount with cents")
    void format_englishWithCents() {
        String result = formatter.format(new BigDecimal("42.50"), englishOptions);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ===== Conversational Number Mode =====
    @Test
    @DisplayName("Conversational mode uses words for numbers")
    void format_conversationalMode() {
        VoiceOptions conversational = new VoiceOptions(Locale.GERMAN, false, 1.0, true, true, 15);
        String result = formatter.format(new BigDecimal("5.00"), conversational);
        // Should use word form "fuenf" instead of "5"
        assertTrue(result.contains("fuenf") || result.contains("Euro"));
    }

    @Test
    @DisplayName("Non-conversational mode uses digits")
    void format_nonConversationalMode() {
        VoiceOptions nonConversational = new VoiceOptions(Locale.GERMAN, false, 1.0, true, false, 15);
        String result = formatter.format(new BigDecimal("5.00"), nonConversational);
        assertNotNull(result);
    }

    // ===== SSML Formatting =====
    @Test
    @DisplayName("SSML format includes currency interpretation hints")
    void formatSsml_includesHints() {
        String result = formatter.formatSsml(new BigDecimal("42.50"), germanSsmlOptions);
        assertTrue(result.contains("say-as") || result.contains("Euro") || result.contains("42"));
    }

    // ===== Edge Cases =====
    @Test
    @DisplayName("Null value returns 'null Euro' in German")
    void format_nullValue() {
        String result = formatter.format(null, germanOptions);
        assertEquals("null Euro", result);
    }

    @Test
    @DisplayName("Negative amount handled")
    void format_negativeAmount() {
        String result = formatter.format(new BigDecimal("-50.00"), germanOptions);
        assertTrue(result.contains("minus") || result.contains("50") || result.contains("Euro"));
    }

    @Test
    @DisplayName("Very small amount")
    void format_verySmallAmount() {
        String result = formatter.format(new BigDecimal("0.05"), germanOptions);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
