package com.voicebanking.agent.text;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BrandEnforcer.
 * Tests detection of prohibited phrases in banking responses.
 */
@DisplayName("BrandEnforcer Tests")
class BrandEnforcerTest {

    private BrandEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new BrandEnforcer();
    }

    // ===== Clean Text Tests =====
    @Test
    @DisplayName("Clean text passes validation")
    void check_cleanText_compliant() {
        String cleanText = "Ihr aktueller Kontostand betraegt 1.234,56 Euro.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(cleanText);
        assertTrue(result.compliant());
    }

    @Test
    @DisplayName("Clean text returns no violations")
    void check_cleanText_noViolations() {
        String cleanText = "Vielen Dank fuer Ihre Anfrage.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(cleanText);
        assertTrue(result.violations().isEmpty());
    }

    // ===== Prohibited Phrase Detection =====
    @Test
    @DisplayName("Detects 'kostenlos'")
    void check_detectsKostenlos() {
        String text = "Dieser Service ist kostenlos fuer Sie.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
        assertTrue(result.violations().stream().anyMatch(v -> v.contains("kostenlos")));
    }

    @Test
    @DisplayName("Detects 'gratis'")
    void check_detectsGratis() {
        String text = "Erhalten Sie eine gratis Kreditkarte.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
    }

    @Test
    @DisplayName("Detects 'garantiert'")
    void check_detectsGarantiert() {
        String text = "Das ist garantiert der beste Zinssatz.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
    }

    @Test
    @DisplayName("Detects 'versprochen'")
    void check_detectsVersprochen() {
        String text = "Wir haben Ihnen versprochen, dass...";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
    }

    @Test
    @DisplayName("Detects 'risikofrei'")
    void check_detectsRisikofrei() {
        String text = "Diese Anlage ist risikofrei.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
    }

    @Test
    @DisplayName("Detects 'ohne risiko'")
    void check_detectsOhneRisiko() {
        String text = "Bei uns gibt es ohne risiko Anlagen.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
    }

    // ===== Violation Messages =====
    @Test
    @DisplayName("Returns violation for prohibited phrase")
    void check_returnsViolationMessage() {
        String text = "Dieses Angebot ist kostenlos.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.violations().isEmpty());
        assertTrue(result.violations().get(0).toLowerCase().contains("kostenlos"));
    }

    @Test
    @DisplayName("Returns multiple violations for multiple prohibited phrases")
    void check_multipleViolations() {
        String text = "Dieser kostenlose Service ist garantiert risikofrei.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertTrue(result.violations().size() >= 2);
    }

    // ===== Case Sensitivity =====
    @Test
    @DisplayName("Detection is case-insensitive")
    void check_caseInsensitive() {
        assertFalse(enforcer.check("KOSTENLOS").compliant());
        assertFalse(enforcer.check("Kostenlos").compliant());
        assertFalse(enforcer.check("KoStEnLoS").compliant());
    }

    // ===== Edge Cases =====
    @Test
    @DisplayName("Empty text is compliant")
    void check_emptyText() {
        assertTrue(enforcer.check("").compliant());
    }

    @Test
    @DisplayName("Null text is compliant")
    void check_nullText() {
        assertTrue(enforcer.check(null).compliant());
    }

    @Test
    @DisplayName("Whitespace-only text is compliant")
    void check_whitespaceOnly() {
        assertTrue(enforcer.check("   \t\n  ").compliant());
    }

    @Test
    @DisplayName("Partial word match triggers detection")
    void check_partialWordMatch() {
        // "kostenloses" contains "kostenlos"
        String text = "Das ist kostenloses Angebot.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertFalse(result.compliant());
    }

    @Test
    @DisplayName("Non-prohibited words pass")
    void check_nonProhibitedWords() {
        String text = "Das ist kostenpflichtig.";
        BrandEnforcer.BrandCheckResult result = enforcer.check(text);
        assertTrue(result.compliant());
    }
}
