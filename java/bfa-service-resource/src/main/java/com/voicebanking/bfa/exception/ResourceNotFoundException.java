package com.voicebanking.bfa.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found.
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
public class ResourceNotFoundException extends BfaException {
    
    private final String resourceType;
    private final String resourceId;
    
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(
            "RESOURCE_NOT_FOUND",
            String.format("%s not found: %s", resourceType, resourceId),
            HttpStatus.NOT_FOUND
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceId() {
        return resourceId;
    }
}
