package com.voicebanking.bfa.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares legitimation requirements for resource access.
 * 
 * <p>The LegitimationInterceptor reads this annotation and verifies that
 * the authenticated user has access to the specific resource.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @RequiresLegitimation(scope = "VIEW_BALANCE", resourceParam = "accountId")}
 * public ResponseEntity<BalanceDto> getBalance(@PathVariable String accountId) { ... }
 * </pre>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLegitimation {
    
    /**
     * The legitimation scope to check (e.g., "VIEW_BALANCE", "INITIATE_TRANSFER").
     */
    String scope();
    
    /**
     * The name of the path variable or request parameter containing the resource ID.
     * If empty, legitimation is checked at user level without resource specificity.
     */
    String resourceParam() default "";
    
    /**
     * The resource type for audit logging (e.g., "ACCOUNT", "CARD").
     */
    String resourceType() default "";
}
