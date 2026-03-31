package com.voicebanking.bfa.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint for audit logging.
 * 
 * <p>By default, all endpoints are audited. This annotation allows
 * customizing the audit behavior.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    
    /**
     * Operation name for audit log. Defaults to method name.
     */
    String operation() default "";
    
    /**
     * Whether to include request body in audit. Default false for security.
     */
    boolean includeRequestBody() default false;
    
    /**
     * Whether to include response body in audit. Default false for performance.
     */
    boolean includeResponseBody() default false;
    
    /**
     * Risk level for this operation (LOW, MEDIUM, HIGH, CRITICAL).
     */
    String riskLevel() default "LOW";
}
