package com.voicebanking.bfa.exception;

import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Exception thrown when required consents are missing.
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
public class ConsentRequiredException extends BfaException {
    
    private final Set<String> requiredConsents;
    private final Set<String> missingConsents;
    
    public ConsentRequiredException(Set<String> required, Set<String> missing) {
        super(
            "CONSENT_REQUIRED",
            "Missing required consents: " + missing,
            HttpStatus.FORBIDDEN
        );
        this.requiredConsents = required;
        this.missingConsents = missing;
    }
    
    public Set<String> getRequiredConsents() {
        return requiredConsents;
    }
    
    public Set<String> getMissingConsents() {
        return missingConsents;
    }
}
