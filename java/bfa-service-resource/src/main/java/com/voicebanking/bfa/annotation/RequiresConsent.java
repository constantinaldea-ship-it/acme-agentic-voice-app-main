package com.voicebanking.bfa.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares required consents for an endpoint.
 * 
 * <p>The ConsentInterceptor reads this annotation and verifies that
 * the user's session has all required consents before allowing access.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @RequiresConsent({"VIEW_BALANCE", "AI_INTERACTION"})}
 * public ResponseEntity<BalanceDto> getBalance(...) { ... }
 * </pre>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresConsent {
    
    /**
     * Array of consent types required for this endpoint.
     * All listed consents must be present in the user's session.
     */
    String[] value();
    
    /**
     * If true, missing consents result in a prompt response rather than rejection.
     * Default is false (strict enforcement).
     */
    boolean promptIfMissing() default false;
}
