package com.voicebanking.bfa.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for Response PEP masking logic.
 *
 * @author Copilot
 * @since 2026-01-17
 */
class ResponsePepFilterTest {

    @Test
    @DisplayName("mask() keeps last 4 characters")
    void maskKeepsLast4() {
        assertEquals("****0 00", ResponsePepFilter.mask("DE89 3704 0044 0532 0130 00"));
    }

    @Test
    @DisplayName("mask() handles short values")
    void maskHandlesShortValues() {
        assertEquals("****", ResponsePepFilter.mask("AB"));
        assertEquals("****", ResponsePepFilter.mask("ABCD"));
    }

    @Test
    @DisplayName("mask() handles null")
    void maskHandlesNull() {
        assertEquals("****", ResponsePepFilter.mask(null));
    }

    @Test
    @DisplayName("mask() handles exactly 5 characters")
    void maskHandlesFiveChars() {
        assertEquals("****BCDE", ResponsePepFilter.mask("ABCDE"));
    }
}
