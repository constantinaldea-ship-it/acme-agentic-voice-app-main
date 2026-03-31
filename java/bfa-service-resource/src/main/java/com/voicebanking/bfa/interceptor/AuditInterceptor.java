package com.voicebanking.bfa.interceptor;

import com.voicebanking.bfa.annotation.Audited;
import com.voicebanking.bfa.filter.BfaSecurityFilter;
import com.voicebanking.bfa.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;

/**
 * Interceptor that logs audit events for API operations.
 * 
 * <p>This interceptor:</p>
 * <ul>
 *   <li>Logs request start with user, endpoint, and context</li>
 *   <li>Logs response completion with status and duration</li>
 *   <li>Respects @Audited annotation for customization</li>
 * </ul>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);
    private static final String ATTR_START_TIME = "bfa.audit.startTime";
    
    private final AuditService auditService;
    
    public AuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                             Object handler) throws Exception {
        request.setAttribute(ATTR_START_TIME, Instant.now());
        
        if (handler instanceof HandlerMethod handlerMethod) {
            String correlationId = (String) request.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);
            String userId = (String) request.getAttribute(BfaSecurityFilter.ATTR_USER_ID);
            String agentId = (String) request.getAttribute(BfaSecurityFilter.ATTR_AGENT_ID);
            String toolId = (String) request.getAttribute(BfaSecurityFilter.ATTR_TOOL_ID);
            String sessionId = (String) request.getAttribute(BfaSecurityFilter.ATTR_SESSION_ID);
            
            String operation = getOperationName(handlerMethod);
            
            auditService.logRequestStart(
                correlationId,
                userId,
                agentId,
                toolId,
                sessionId,
                operation,
                request.getMethod(),
                request.getRequestURI()
            );
        }
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) throws Exception {
        // Response body not yet written - can't audit response here
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        
        Instant startTime = (Instant) request.getAttribute(ATTR_START_TIME);
        long durationMs = startTime != null 
            ? Instant.now().toEpochMilli() - startTime.toEpochMilli() 
            : -1;
        
        String correlationId = (String) request.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);
        String userId = (String) request.getAttribute(BfaSecurityFilter.ATTR_USER_ID);
        String agentId = (String) request.getAttribute(BfaSecurityFilter.ATTR_AGENT_ID);
        String toolId = (String) request.getAttribute(BfaSecurityFilter.ATTR_TOOL_ID);
        String sessionId = (String) request.getAttribute(BfaSecurityFilter.ATTR_SESSION_ID);
        String operation = getOperationName(handlerMethod);
        
        String outcome = ex != null ? "FAILURE" : (response.getStatus() < 400 ? "SUCCESS" : "ERROR");
        String errorMessage = ex != null ? ex.getMessage() : null;
        
        auditService.logRequestComplete(
            correlationId,
            userId,
            agentId,
            toolId,
            sessionId,
            operation,
            outcome,
            response.getStatus(),
            durationMs,
            errorMessage
        );
    }
    
    private String getOperationName(HandlerMethod handlerMethod) {
        Audited audited = handlerMethod.getMethodAnnotation(Audited.class);
        if (audited != null && !audited.operation().isEmpty()) {
            return audited.operation();
        }
        
        // Default: ClassName.methodName
        return handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName();
    }
}
