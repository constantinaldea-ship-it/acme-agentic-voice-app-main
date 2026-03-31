package com.voicebanking.bfa.interceptor;

import com.voicebanking.bfa.annotation.RequiresConsent;
import com.voicebanking.bfa.exception.ConsentRequiredException;
import com.voicebanking.bfa.filter.BfaSecurityFilter;
import com.voicebanking.bfa.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashSet;
import java.util.Set;

/**
 * Interceptor that enforces consent requirements declared via @RequiresConsent.
 * 
 * <p>This interceptor:</p>
 * <ul>
 *   <li>Reads @RequiresConsent annotation from the handler method</li>
 *   <li>Queries the consent service for user's granted consents</li>
 *   <li>Rejects requests where required consents are missing</li>
 * </ul>
 * 
 * <p>Consents are verified against SERVER-SIDE session state, not client-provided values.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Component
public class ConsentInterceptor implements HandlerInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(ConsentInterceptor.class);
    
    private final ConsentService consentService;
    
    public ConsentInterceptor(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        
        // Check for @RequiresConsent on method or class
        RequiresConsent annotation = handlerMethod.getMethodAnnotation(RequiresConsent.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequiresConsent.class);
        }
        
        if (annotation == null) {
            // No consent requirements
            return true;
        }
        
        String userId = (String) request.getAttribute(BfaSecurityFilter.ATTR_USER_ID);
        String sessionId = (String) request.getAttribute(BfaSecurityFilter.ATTR_SESSION_ID);
        String correlationId = (String) request.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);
        
        Set<String> requiredConsents = Set.of(annotation.value());
        Set<String> grantedConsents = consentService.getGrantedConsents(userId, sessionId);
        
        Set<String> missingConsents = new HashSet<>(requiredConsents);
        missingConsents.removeAll(grantedConsents);
        
        if (!missingConsents.isEmpty()) {
            log.warn("[{}] Consent check failed for user {}: missing {}", 
                     correlationId, userId, missingConsents);
            
            if (annotation.promptIfMissing()) {
                // Could return a prompt response instead of outright rejection
                request.setAttribute("bfa.consentPrompt", missingConsents);
            }
            
            throw new ConsentRequiredException(requiredConsents, missingConsents);
        }
        
        log.debug("[{}] Consent check passed for user {}: {}", correlationId, userId, requiredConsents);
        return true;
    }
}
