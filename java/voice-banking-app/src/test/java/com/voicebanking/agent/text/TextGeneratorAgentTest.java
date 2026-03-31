package com.voicebanking.agent.text;

import com.voicebanking.agent.text.domain.FormattedResponse;
import com.voicebanking.agent.text.domain.ResponseType;
import com.voicebanking.agent.text.formatter.*;
import com.voicebanking.agent.text.template.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TextGeneratorAgent.
 * Tests the 5 tools: generateResponse, formatForVoice, formatCurrency, formatDate, formatAccountNumber.
 */
@DisplayName("TextGeneratorAgent Tests")
class TextGeneratorAgentTest {

    private TextGeneratorAgent agent;

    @BeforeEach
    void setUp() {
        NumberFormatter numberFormatter = new NumberFormatter();
        agent = new TextGeneratorAgent(
            new TemplateEngine(),
            new CurrencyFormatter(numberFormatter),
            new DateFormatter(),
            new AccountFormatter(),
            numberFormatter,
            new BrandEnforcer()
        );
    }

    // ===== Agent Identity =====
    @Test
    @DisplayName("Agent has correct ID")
    void agentId() {
        assertEquals("text-generator", agent.getAgentId());
    }

    @Test
    @DisplayName("Agent has description")
    void agentDescription() {
        assertNotNull(agent.getDescription());
        assertFalse(agent.getDescription().isEmpty());
    }

    @Test
    @DisplayName("Agent exposes 5 tools")
    void agentTools() {
        List<String> tools = agent.getToolIds();
        assertEquals(5, tools.size());
        assertTrue(tools.contains("generateResponse"));
        assertTrue(tools.contains("formatForVoice"));
        assertTrue(tools.contains("formatCurrency"));
        assertTrue(tools.contains("formatDate"));
        assertTrue(tools.contains("formatAccountNumber"));
    }

    // ===== generateResponse Tool =====
    @Test
    @DisplayName("generateResponse: balance response")
    void executeGenerateResponse_balance() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "BALANCE");
        params.put("templateId", "balance.single.de");
        params.put("language", "de");
        
        Map<String, Object> data = new HashMap<>();
        data.put("balance", "1.234,56 Euro");
        params.put("data", data);
        
        Map<String, Object> result = agent.executeTool("generateResponse", params);
        assertTrue((Boolean) result.get("success"));
        assertNotNull(result.get("result"));
        assertTrue(result.get("result") instanceof FormattedResponse);
    }

    @Test
    @DisplayName("generateResponse: error response")
    void executeGenerateResponse_error() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "ERROR");
        params.put("templateId", "error-generic");
        params.put("language", "de");
        
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Konto nicht gefunden");
        params.put("data", data);
        
        Map<String, Object> result = agent.executeTool("generateResponse", params);
        assertTrue((Boolean) result.get("success"));
    }

    // ===== formatForVoice Tool =====
    @Test
    @DisplayName("formatForVoice: converts text to voice-friendly format")
    void executeFormatForVoice() {
        Map<String, Object> params = new HashMap<>();
        params.put("text", "Ihr Kontostand betraegt 1234.56 EUR");
        params.put("language", "de");
        params.put("ssmlEnabled", "false");
        
        Map<String, Object> result = agent.executeTool("formatForVoice", params);
        assertTrue((Boolean) result.get("success"));
    }

    @Test
    @DisplayName("formatForVoice: expands & to 'and'")
    void executeFormatForVoice_expandsAmpersand() {
        Map<String, Object> params = new HashMap<>();
        params.put("text", "Terms & Conditions");
        params.put("language", "en");
        
        Map<String, Object> result = agent.executeTool("formatForVoice", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertTrue(formatted.contains("and"));
        assertFalse(formatted.contains("&"));
    }

    @Test
    @DisplayName("formatForVoice: expands € to Euro")
    void executeFormatForVoice_expandsEuroSymbol() {
        Map<String, Object> params = new HashMap<>();
        params.put("text", "Balance: €100");
        params.put("language", "de");
        
        Map<String, Object> result = agent.executeTool("formatForVoice", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertTrue(formatted.contains("Euro"));
    }

    // ===== formatCurrency Tool =====
    @Test
    @DisplayName("formatCurrency: German locale")
    void executeFormatCurrency_german() {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", new BigDecimal("1234.56"));
        params.put("language", "de");
        params.put("conversationalNumbers", "true");
        
        Map<String, Object> result = agent.executeTool("formatCurrency", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertTrue(formatted.contains("Euro") || formatted.contains("tausend"));
    }

    @Test
    @DisplayName("formatCurrency: English locale")
    void executeFormatCurrency_english() {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", new BigDecimal("100.00"));
        params.put("language", "en");
        params.put("conversationalNumbers", "true");
        
        Map<String, Object> result = agent.executeTool("formatCurrency", params);
        assertTrue((Boolean) result.get("success"));
    }

    @Test
    @DisplayName("formatCurrency: non-conversational")
    void executeFormatCurrency_nonConversational() {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", new BigDecimal("42.50"));
        params.put("language", "de");
        params.put("conversationalNumbers", "false");
        
        Map<String, Object> result = agent.executeTool("formatCurrency", params);
        assertTrue((Boolean) result.get("success"));
    }

    @Test
    @DisplayName("formatCurrency: accepts string amount")
    void executeFormatCurrency_stringAmount() {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", "99.99");
        params.put("language", "de");
        
        Map<String, Object> result = agent.executeTool("formatCurrency", params);
        assertTrue((Boolean) result.get("success"));
    }

    // ===== formatDate Tool =====
    @Test
    @DisplayName("formatDate: relative date (today)")
    void executeFormatDate_today() {
        Map<String, Object> params = new HashMap<>();
        params.put("date", LocalDate.now().toString());
        params.put("language", "de");
        params.put("useRelativeDates", "true");
        
        Map<String, Object> result = agent.executeTool("formatDate", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertEquals("heute", formatted);
    }

    @Test
    @DisplayName("formatDate: relative date (yesterday)")
    void executeFormatDate_yesterday() {
        Map<String, Object> params = new HashMap<>();
        params.put("date", LocalDate.now().minusDays(1).toString());
        params.put("language", "de");
        params.put("useRelativeDates", "true");
        
        Map<String, Object> result = agent.executeTool("formatDate", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertEquals("gestern", formatted);
    }

    @Test
    @DisplayName("formatDate: English locale today")
    void executeFormatDate_englishToday() {
        Map<String, Object> params = new HashMap<>();
        params.put("date", LocalDate.now().toString());
        params.put("language", "en");
        params.put("useRelativeDates", "true");
        
        Map<String, Object> result = agent.executeTool("formatDate", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertEquals("today", formatted);
    }

    @Test
    @DisplayName("formatDate: accepts LocalDate directly")
    void executeFormatDate_localDate() {
        Map<String, Object> params = new HashMap<>();
        params.put("date", LocalDate.of(2024, 6, 15));
        params.put("language", "de");
        params.put("useRelativeDates", "false");
        
        Map<String, Object> result = agent.executeTool("formatDate", params);
        assertTrue((Boolean) result.get("success"));
    }

    // ===== formatAccountNumber Tool =====
    @Test
    @DisplayName("formatAccountNumber: masks IBAN")
    void executeFormatAccountNumber_iban() {
        Map<String, Object> params = new HashMap<>();
        params.put("accountNumber", "DE89370400440532013000");
        params.put("language", "de");
        
        Map<String, Object> result = agent.executeTool("formatAccountNumber", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        // Should mask most digits
        assertTrue(formatted.contains("3000") || formatted.contains("X"));
    }

    @Test
    @DisplayName("formatAccountNumber: short account")
    void executeFormatAccountNumber_short() {
        Map<String, Object> params = new HashMap<>();
        params.put("accountNumber", "1234");
        params.put("language", "de");
        
        Map<String, Object> result = agent.executeTool("formatAccountNumber", params);
        assertTrue((Boolean) result.get("success"));
    }

    // ===== Error Handling =====
    @Test
    @DisplayName("Unknown tool throws exception")
    void executeTool_unknownTool() {
        Map<String, Object> params = new HashMap<>();
        assertThrows(IllegalArgumentException.class, 
            () -> agent.executeTool("unknownTool", params));
    }

    // ===== Default Parameters =====
    @Test
    @DisplayName("Uses German locale by default")
    void executeFormatDate_defaultLocale() {
        Map<String, Object> params = new HashMap<>();
        params.put("date", LocalDate.now().toString());
        // No language specified
        
        Map<String, Object> result = agent.executeTool("formatDate", params);
        assertTrue((Boolean) result.get("success"));
        String formatted = (String) result.get("result");
        assertEquals("heute", formatted); // German default
    }
}
