package com.voicebanking.bfa.interceptor;

import com.voicebanking.bfa.annotation.RequiresLegitimation;
import com.voicebanking.bfa.exception.LegitimationDeniedException;
import com.voicebanking.bfa.filter.BfaSecurityFilter;
import com.voicebanking.bfa.service.LegitimationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Interceptor that enforces legitimation (resource-level access control).
 * 
 * <p>This interceptor:</p>
 * <ul>
 *   <li>Reads @RequiresLegitimation annotation from the handler method</li>
 *   <li>Extracts resource ID from path variables</li>
 *   <li>Calls legitimation service to verify access</li>
 *   <li>Rejects requests where legitimation fails</li>
 * </ul>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Component
public class LegitimationInterceptor implements HandlerInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(LegitimationInterceptor.class);
    
    private final LegitimationService legitimationService;
    
    public LegitimationInterceptor(LegitimationService legitimationService) {
        this.legitimationService = legitimationService;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        
        // Check for @RequiresLegitimation on method or class
        RequiresLegitimation annotation = handlerMethod.getMethodAnnotation(RequiresLegitimation.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequiresLegitimation.class);
        }
        
        if (annotation == null) {
            // No legitimation requirements
            return true;
        }
        
        String userId = (String) request.getAttribute(BfaSecurityFilter.ATTR_USER_ID);
        String correlationId = (String) request.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);
        
        String scope = annotation.scope();
        String resourceType = annotation.resourceType();
        String resourceParam = annotation.resourceParam();
        
        String resourceId = null;
        if (!resourceParam.isEmpty()) {
            resourceId = extractResourceId(request, resourceParam);
        }
        
        boolean authorized = legitimationService.checkAccess(userId, scope, resourceType, resourceId);
        
        if (!authorized) {
            log.warn("[{}] Legitimation denied for user {} on {}/{} (scope: {})", 
                     correlationId, userId, resourceType, resourceId, scope);
            throw new LegitimationDeniedException(resourceType, resourceId, scope);
        }
        
        log.debug("[{}] Legitimation passed for user {} on {}/{}", 
                  correlationId, userId, resourceType, resourceId);
        return true;
    }
    
    @SuppressWarnings("unchecked")
    private String extractResourceId(HttpServletRequest request, String paramName) {
        // First try path variables
        Map<String, String> pathVariables = (Map<String, String>) 
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        
        if (pathVariables != null && pathVariables.containsKey(paramName)) {
            return pathVariables.get(paramName);
        }
        
        // Then try request parameters
        return request.getParameter(paramName);
    }
}
