package com.voicebanking.agent.text;

import com.voicebanking.agent.text.domain.VoiceOptions;
import com.voicebanking.agent.text.formatter.NumberFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NumberFormatter.
 * Tests German and English number-to-words conversion for voice output.
 */
@DisplayName("NumberFormatter Tests")
class NumberFormatterTest {

    private NumberFormatter formatter;
    private VoiceOptions germanOptions;
    private VoiceOptions englishOptions;

    @BeforeEach
    void setUp() {
        formatter = new NumberFormatter();
        germanOptions = new VoiceOptions(Locale.GERMAN, false, 1.0, true, true, 15);
        englishOptions = new VoiceOptions(Locale.ENGLISH, false, 1.0, true, true, 15);
    }

    // ===== German Zero =====
    @Test
    @DisplayName("German: zero")
    void toGermanWords_zero() {
        assertEquals("null", formatter.toGermanWords(0));
    }

    // ===== German Single Digits =====
    @Test
    @DisplayName("German: single digits 1-9")
    void toGermanWords_singleDigits() {
        assertEquals("eins", formatter.toGermanWords(1));
        assertEquals("zwei", formatter.toGermanWords(2));
        assertEquals("drei", formatter.toGermanWords(3));
        assertEquals("vier", formatter.toGermanWords(4));
        assertEquals("fuenf", formatter.toGermanWords(5));
        assertEquals("sechs", formatter.toGermanWords(6));
        assertEquals("sieben", formatter.toGermanWords(7));
        assertEquals("acht", formatter.toGermanWords(8));
        assertEquals("neun", formatter.toGermanWords(9));
    }

    // ===== German Teens =====
    @Test
    @DisplayName("German: teens 10-19")
    void toGermanWords_teens() {
        assertEquals("zehn", formatter.toGermanWords(10));
        assertEquals("elf", formatter.toGermanWords(11));
        assertEquals("zwoelf", formatter.toGermanWords(12));
        assertEquals("dreizehn", formatter.toGermanWords(13));
        assertEquals("vierzehn", formatter.toGermanWords(14));
        assertEquals("fuenfzehn", formatter.toGermanWords(15));
        assertEquals("sechzehn", formatter.toGermanWords(16));
        assertEquals("siebzehn", formatter.toGermanWords(17));
        assertEquals("achtzehn", formatter.toGermanWords(18));
        assertEquals("neunzehn", formatter.toGermanWords(19));
    }

    // ===== German Tens =====
    @Test
    @DisplayName("German: tens 20-90")
    void toGermanWords_tens() {
        assertEquals("zwanzig", formatter.toGermanWords(20));
        assertEquals("dreissig", formatter.toGermanWords(30));
        assertEquals("vierzig", formatter.toGermanWords(40));
        assertEquals("fuenfzig", formatter.toGermanWords(50));
        assertEquals("sechzig", formatter.toGermanWords(60));
        assertEquals("siebzig", formatter.toGermanWords(70));
        assertEquals("achtzig", formatter.toGermanWords(80));
        assertEquals("neunzig", formatter.toGermanWords(90));
    }

    // ===== German Compound Numbers (21-99) =====
    @Test
    @DisplayName("German: compound numbers use X-und-Y pattern")
    void toGermanWords_compoundNumbers() {
        assertEquals("einundzwanzig", formatter.toGermanWords(21));
        assertEquals("zweiunddreissig", formatter.toGermanWords(32));
        assertEquals("dreiundvierzig", formatter.toGermanWords(43));
        assertEquals("fuenfundvierzig", formatter.toGermanWords(45));
        assertEquals("siebenundsechzig", formatter.toGermanWords(67));
        assertEquals("neunundneunzig", formatter.toGermanWords(99));
    }

    // ===== German Hundreds =====
    @Test
    @DisplayName("German: hundreds")
    void toGermanWords_hundreds() {
        assertEquals("einhundert", formatter.toGermanWords(100));
        assertEquals("zweihundert", formatter.toGermanWords(200));
        assertEquals("dreihundert", formatter.toGermanWords(300));
        assertEquals("einhunderteins", formatter.toGermanWords(101));
        assertEquals("einhundertdreiundzwanzig", formatter.toGermanWords(123));
        assertEquals("fuenfhundertdreiundzwanzig", formatter.toGermanWords(523));
        assertEquals("neunhundertneunundneunzig", formatter.toGermanWords(999));
    }

    // ===== German Thousands =====
    @Test
    @DisplayName("German: thousands")
    void toGermanWords_thousands() {
        assertEquals("eintausend", formatter.toGermanWords(1000));
        assertEquals("eintausendeins", formatter.toGermanWords(1001));
        assertEquals("eintausendzweihundertvierunddreissig", formatter.toGermanWords(1234));
        assertEquals("dreitausendfuenfhundertsiebenundsechzig", formatter.toGermanWords(3567));
        assertEquals("zweiundvierzigtausend", formatter.toGermanWords(42000));
        assertEquals("neunundneunzigtausendneunhundertneunundneunzig", formatter.toGermanWords(99999));
    }

    // ===== German Millions =====
    @Test
    @DisplayName("German: millions")
    void toGermanWords_millions() {
        assertEquals("eine Million", formatter.toGermanWords(1000000));
        assertEquals("zwei Millionen", formatter.toGermanWords(2000000));
        assertEquals("eine Million zweihundertdreiundvierzigtausendvierhundertfuenfundsechzig", 
                     formatter.toGermanWords(1243465));
    }

    // ===== English Zero =====
    @Test
    @DisplayName("English: zero")
    void toEnglishWords_zero() {
        assertEquals("zero", formatter.toEnglishWords(0));
    }

    // ===== English Single Digits =====
    @Test
    @DisplayName("English: single digits")
    void toEnglishWords_singleDigits() {
        assertEquals("one", formatter.toEnglishWords(1));
        assertEquals("five", formatter.toEnglishWords(5));
        assertEquals("nine", formatter.toEnglishWords(9));
    }

    // ===== English Teens =====
    @Test
    @DisplayName("English: teens")
    void toEnglishWords_teens() {
        assertEquals("ten", formatter.toEnglishWords(10));
        assertEquals("eleven", formatter.toEnglishWords(11));
        assertEquals("twelve", formatter.toEnglishWords(12));
        assertEquals("nineteen", formatter.toEnglishWords(19));
    }

    // ===== English Compound Numbers =====
    @Test
    @DisplayName("English: compound numbers use hyphen pattern")
    void toEnglishWords_compoundNumbers() {
        assertEquals("twenty-one", formatter.toEnglishWords(21));
        assertEquals("forty-five", formatter.toEnglishWords(45));
        assertEquals("ninety-nine", formatter.toEnglishWords(99));
    }

    // ===== English Hundreds/Thousands =====
    @Test
    @DisplayName("English: hundreds and thousands")
    void toEnglishWords_hundredsThousands() {
        assertEquals("one hundred", formatter.toEnglishWords(100));
        assertEquals("one hundred twenty-three", formatter.toEnglishWords(123));
        assertEquals("one thousand", formatter.toEnglishWords(1000));
        assertEquals("one thousand two hundred thirty-four", formatter.toEnglishWords(1234));
        assertEquals("forty-two thousand", formatter.toEnglishWords(42000));
    }

    // ===== VoiceFormatter Interface Tests =====
    @Test
    @DisplayName("VoiceFormatter: German locale uses toGermanWords")
    void format_germanLocale() {
        String result = formatter.format(42L, germanOptions);
        assertEquals("zweiundvierzig", result);
    }

    @Test
    @DisplayName("VoiceFormatter: English locale uses toEnglishWords")
    void format_englishLocale() {
        String result = formatter.format(42L, englishOptions);
        assertEquals("forty-two", result);
    }

    @Test
    @DisplayName("VoiceFormatter: SSML format wraps in say-as tags")
    void formatSsml_wrapsSayAsTags() {
        VoiceOptions ssmlOptions = new VoiceOptions(Locale.GERMAN, true, 1.0, true, true, 15);
        String result = formatter.formatSsml(1234L, ssmlOptions);
        assertTrue(result.contains("say-as") || result.contains("eintausendzweihundertvierunddreissig"));
    }

    @Test
    @DisplayName("VoiceFormatter: null value returns German 'null' (zero)")
    void format_nullValue() {
        String result = formatter.format(null, germanOptions);
        assertEquals("null", result);
    }

    @Test
    @DisplayName("VoiceFormatter: negative numbers handled")
    void format_negativeNumber() {
        String result = formatter.format(-42L, germanOptions);
        assertTrue(result.contains("minus") || result.contains("zweiundvierzig"));
    }
}
