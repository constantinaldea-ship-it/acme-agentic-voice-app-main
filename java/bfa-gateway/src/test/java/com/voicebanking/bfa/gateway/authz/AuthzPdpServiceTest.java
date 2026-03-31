package com.voicebanking.bfa.gateway.authz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link AuthzPdpService}.
 *
 * @author Copilot
 * @since 2026-01-17
 */
class AuthzPdpServiceTest {

    @Test
    @DisplayName("default PERMIT decision is returned")
    void defaultPermit() {
        AuthzPdpService service = new AuthzPdpService("PERMIT");
        assertEquals(AuthzPdpService.Decision.PERMIT,
                service.evaluate("user-1", "branch-finder", "corr-001"));
    }

    @Test
    @DisplayName("default DENY decision is returned")
    void defaultDeny() {
        AuthzPdpService service = new AuthzPdpService("DENY");
        assertEquals(AuthzPdpService.Decision.DENY,
                service.evaluate("user-1", "branch-finder", "corr-002"));
    }

    @Test
    @DisplayName("decision is case-insensitive")
    void caseInsensitive() {
        AuthzPdpService service = new AuthzPdpService("permit");
        assertEquals(AuthzPdpService.Decision.PERMIT,
                service.evaluate("user-1", "branch-finder", "corr-003"));
    }
}
