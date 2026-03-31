package com.voicebanking.bfa.gateway.authz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stub AuthZ PDP (Policy Decision Point) service.
 *
 * <p>In production this would call the real AuthZ endpoint (e.g. OPA, Cedar,
 * or a bank-internal PDP) to evaluate whether a given principal + action +
 * resource tuple is permitted.  In this demo it always returns the decision
 * configured via {@code bfa.gateway.authz.default-decision}.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 */
@Service
public class AuthzPdpService {

    private static final Logger log = LoggerFactory.getLogger(AuthzPdpService.class);

    /** Possible AuthZ decisions. */
    public enum Decision { PERMIT, DENY }

    private final Decision defaultDecision;

    public AuthzPdpService(
            @Value("${bfa.gateway.authz.default-decision:PERMIT}") String defaultDecision) {
        this.defaultDecision = Decision.valueOf(defaultDecision.toUpperCase());
        log.info("AuthzPdpService initialised in STUB mode — default decision: {}", this.defaultDecision);
    }

    /**
     * Evaluate whether the given principal may invoke the specified tool.
     *
     * @param principal     authenticated user / service identity
     * @param toolName      tool being invoked
     * @param correlationId request correlation ID for audit trail
     * @return PERMIT or DENY
     */
    public Decision evaluate(String principal, String toolName, String correlationId) {
        log.debug("[{}] AuthZ evaluate: principal='{}' tool='{}' → {}",
                correlationId, principal, toolName, defaultDecision);
        return defaultDecision;
    }
}
