package com.voicebanking.agent.text;

import com.voicebanking.agent.text.domain.ResponseType;
import com.voicebanking.agent.text.template.TemplateEngine;
import com.voicebanking.agent.text.template.ResponseTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateEngine and ResponseTemplate.
 */
@DisplayName("TemplateEngine Tests")
class TemplateEngineTest {

    private TemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TemplateEngine();
    }

    // ===== Template Registration =====
    @Test
    @DisplayName("Default templates are loaded")
    void defaultTemplatesLoaded() {
        // German balance template should exist
        assertTrue(engine.getTemplate("balance.single.de").isPresent());
        assertTrue(engine.getTemplate("balance.single.en").isPresent());
    }

    @Test
    @DisplayName("Register custom template")
    void registerCustomTemplate() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "custom.test", ResponseType.INFORMATION, "Hello, ${name}!");
        engine.registerTemplate(template);
        assertTrue(engine.getTemplate("custom.test").isPresent());
    }

    @Test
    @DisplayName("Get template by ID")
    void getTemplateById() {
        Optional<ResponseTemplate> template = engine.getTemplate("balance.single.de");
        assertTrue(template.isPresent());
        assertEquals("balance.single.de", template.get().id());
    }

    @Test
    @DisplayName("Get non-existent template returns empty")
    void getNonExistentTemplate() {
        Optional<ResponseTemplate> template = engine.getTemplate("does.not.exist");
        assertTrue(template.isEmpty());
    }

    // ===== Template Rendering =====
    @Test
    @DisplayName("Render template with variables")
    void renderWithVariables() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test.greeting", ResponseType.GREETING,
            "Guten Tag, ${name}. Ihr Kontostand betraegt ${balance}.");
        
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Herr Mueller");
        data.put("balance", "1.234,56 Euro");
        
        String result = template.render(data);
        assertEquals("Guten Tag, Herr Mueller. Ihr Kontostand betraegt 1.234,56 Euro.", result);
    }

    @Test
    @DisplayName("Render template with missing variable keeps placeholder")
    void renderWithMissingVariable() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test.missing", ResponseType.INFORMATION,
            "Hello, ${name}. Your account is ${account}.");
        
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John");
        // "account" is missing
        
        String result = template.render(data);
        assertTrue(result.contains("John"));
        assertTrue(result.contains("${account}"));
    }

    @Test
    @DisplayName("Render SSML template")
    void renderSsmlTemplate() {
        ResponseTemplate template = ResponseTemplate.withSsml(
            "test.ssml", ResponseType.BALANCE,
            "Your balance is ${balance}.",
            "<speak>Your balance is <break time=\"300ms\"/>${balance}.</speak>");
        
        Map<String, Object> data = new HashMap<>();
        data.put("balance", "one hundred euros");
        
        String result = template.renderSsml(data);
        assertTrue(result.contains("<speak>"));
        assertTrue(result.contains("<break"));
        assertTrue(result.contains("one hundred euros"));
    }

    @Test
    @DisplayName("Render SSML falls back to text if no SSML template")
    void renderSsmlFallback() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test.noSsml", ResponseType.INFORMATION, "Plain text: ${value}");
        
        Map<String, Object> data = new HashMap<>();
        data.put("value", "test");
        
        String result = template.renderSsml(data);
        assertEquals("Plain text: test", result);
    }

    // ===== ResponseTemplate Record Tests =====
    @Test
    @DisplayName("hasSsml returns true when SSML present")
    void hasSsml_true() {
        ResponseTemplate template = ResponseTemplate.withSsml(
            "test", ResponseType.BALANCE, "text", "<speak>ssml</speak>");
        assertTrue(template.hasSsml());
    }

    @Test
    @DisplayName("hasSsml returns false when SSML null")
    void hasSsml_false() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test", ResponseType.BALANCE, "text");
        assertFalse(template.hasSsml());
    }

    // ===== Edge Cases =====
    @Test
    @DisplayName("Empty data map renders template with unsubstituted placeholders")
    void renderEmptyData() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test.empty", ResponseType.INFORMATION,
            "Fixed text without variables");
        
        String result = template.render(new HashMap<>());
        assertEquals("Fixed text without variables", result);
    }

    @Test
    @DisplayName("Template with multiple same variables")
    void renderDuplicateVariables() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test.duplicate", ResponseType.INFORMATION,
            "${name} said hello. ${name} left.");
        
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        
        String result = template.render(data);
        assertEquals("Alice said hello. Alice left.", result);
    }

    @Test
    @DisplayName("Template with special characters in value")
    void renderSpecialCharacters() {
        ResponseTemplate template = ResponseTemplate.textOnly(
            "test.special", ResponseType.INFORMATION,
            "Amount: ${amount}");
        
        Map<String, Object> data = new HashMap<>();
        data.put("amount", "€1.234,56");
        
        String result = template.render(data);
        assertEquals("Amount: €1.234,56", result);
    }
}
