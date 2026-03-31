package com.voicebanking.bfa.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when legitimation check fails.
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
public class LegitimationDeniedException extends BfaException {
    
    private final String resourceType;
    private final String resourceId;
    private final String scope;
    
    public LegitimationDeniedException(String resourceType, String resourceId, String scope) {
        super(
            "LEGITIMATION_DENIED",
            String.format("Access denied to %s/%s for scope %s", resourceType, resourceId, scope),
            HttpStatus.FORBIDDEN
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.scope = scope;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceId() {
        return resourceId;
    }
    
    public String getScope() {
        return scope;
    }
}
