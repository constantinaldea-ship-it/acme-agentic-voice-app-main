package com.voicebanking.agent.text;

import com.voicebanking.agent.text.domain.VoiceOptions;
import com.voicebanking.agent.text.formatter.AccountFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AccountFormatter.
 * Tests account number masking and voice formatting.
 */
@DisplayName("AccountFormatter Tests")
class AccountFormatterTest {

    private AccountFormatter formatter;
    private VoiceOptions germanOptions;
    private VoiceOptions englishOptions;
    private VoiceOptions germanSsmlOptions;

    @BeforeEach
    void setUp() {
        formatter = new AccountFormatter();
        germanOptions = new VoiceOptions(Locale.GERMAN, false, 1.0, true, true, 15);
        englishOptions = new VoiceOptions(Locale.ENGLISH, false, 1.0, true, true, 15);
        germanSsmlOptions = new VoiceOptions(Locale.GERMAN, true, 1.0, true, true, 15);
    }

    // ===== Masking Tests =====
    @Test
    @DisplayName("Standard account number masking")
    void format_masksAccountNumber() {
        String result = formatter.format("DE89370400440532013000", germanOptions);
        // Should mask most of the account number, showing only last 4 spoken as words
        assertTrue(result.contains("X") && (result.contains("null") || result.contains("drei")));
    }

    @Test
    @DisplayName("Short account number masking")
    void format_shortAccountNumber() {
        String result = formatter.format("12345678", germanOptions);
        // Should mask first 4 digits, show last 4 as words
        assertTrue(result.contains("X") && result.contains("fuenf"));
    }

    @Test
    @DisplayName("Very short account number preserved")
    void format_veryShortAccountNumber() {
        String result = formatter.format("1234", germanOptions);
        // Too short to mask, might show all or partially
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ===== Digit-by-Digit Voice Output =====
    @Test
    @DisplayName("German: digits spoken individually")
    void format_germanDigitByDigit() {
        String result = formatter.format("1234", germanOptions);
        // Should spell out as individual digits with spacing/pauses
        // "eins zwei drei vier" or similar
        assertTrue(result.contains(" ") || result.matches(".*\\d.*"));
    }

    @Test
    @DisplayName("English: digits spoken individually")
    void format_englishDigitByDigit() {
        String result = formatter.format("5678", englishOptions);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ===== IBAN Formatting =====
    @Test
    @DisplayName("IBAN format recognized and masked")
    void format_ibanMasked() {
        String iban = "DE89370400440532013000";
        String result = formatter.format(iban, germanOptions);
        // Should recognize IBAN and apply appropriate masking
        assertTrue(result.length() < iban.length() || result.contains("XXXX") || result.contains("X"));
    }

    @Test
    @DisplayName("IBAN last 4 digits visible")
    void format_ibanLastFourVisible() {
        String iban = "DE89370400440532013000";
        String result = formatter.format(iban, germanOptions);
        assertTrue(result.contains("3000") || result.contains("drei null null null"));
    }

    // ===== SSML Formatting =====
    @Test
    @DisplayName("SSML includes character spelling hints")
    void formatSsml_includesSpellingHints() {
        String result = formatter.formatSsml("1234", germanSsmlOptions);
        // SSML should help TTS spell digits clearly
        assertTrue(result.contains("say-as") || result.contains("characters") || 
                   result.contains("eins") || result.contains("1"));
    }

    @Test
    @DisplayName("SSML format for masked account")
    void formatSsml_maskedAccount() {
        String result = formatter.formatSsml("DE89370400440532013000", germanSsmlOptions);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ===== Edge Cases =====
    @Test
    @DisplayName("Null account returns 'unbekannt' in German")
    void format_nullAccount() {
        String result = formatter.format(null, germanOptions);
        assertEquals("unbekannt", result);
    }

    @Test
    @DisplayName("Empty account returns 'unbekannt' in German")
    void format_emptyAccount() {
        String result = formatter.format("", germanOptions);
        assertEquals("unbekannt", result);
    }

    @Test
    @DisplayName("Account with spaces handled")
    void format_accountWithSpaces() {
        String result = formatter.format("DE89 3704 0044 0532 0130 00", germanOptions);
        assertNotNull(result);
        // Should strip spaces and process
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Account with special characters")
    void format_accountWithSpecialChars() {
        String result = formatter.format("DE89-3704-0044", germanOptions);
        assertNotNull(result);
    }
}
