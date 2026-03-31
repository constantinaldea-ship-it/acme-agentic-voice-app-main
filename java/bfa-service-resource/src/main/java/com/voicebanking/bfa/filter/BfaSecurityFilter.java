package com.voicebanking.bfa.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Security filter for authentication and request context setup.
 * 
 * <p>This filter:</p>
 * <ul>
 *   <li>Generates and sets correlation ID for tracing</li>
 *   <li>Extracts and validates Authorization header</li>
 *   <li>Sets authenticated user in request attributes</li>
 *   <li>Extracts agent context headers (X-Agent-Id, X-Tool-Id, X-Session-Id)</li>
 * </ul>
 * 
 * <p>Security is enforced BEFORE Spring MVC dispatching.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Component
@Order(1)
public class BfaSecurityFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(BfaSecurityFilter.class);
    
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String AGENT_ID_HEADER = "X-Agent-Id";
    public static final String TOOL_ID_HEADER = "X-Tool-Id";
    public static final String SESSION_ID_HEADER = "X-Session-Id";
    
    public static final String ATTR_USER_ID = "bfa.userId";
    public static final String ATTR_AGENT_ID = "bfa.agentId";
    public static final String ATTR_TOOL_ID = "bfa.toolId";
    public static final String ATTR_SESSION_ID = "bfa.sessionId";
    public static final String ATTR_CORRELATION_ID = "bfa.correlationId";
    
    @Value("${bfa.security.enabled:true}")
    private boolean securityEnabled;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                     FilterChain filterChain) throws ServletException, IOException {
        // Generate or extract correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        // Set MDC for logging
        MDC.put("correlationId", correlationId);
        request.setAttribute(ATTR_CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        try {
            // Skip security for health/actuator endpoints
            if (isExcludedPath(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Extract agent context (for observability, not authorization)
            extractAgentContext(request);
            
            if (securityEnabled) {
                // Validate authentication
                String userId = validateAuthentication(request);
                if (userId == null) {
                    log.warn("[{}] Authentication failed for {}", correlationId, request.getRequestURI());
                    sendUnauthorized(response, correlationId);
                    return;
                }
                request.setAttribute(ATTR_USER_ID, userId);
                MDC.put("userId", userId);
            } else {
                // Development mode: use mock user
                request.setAttribute(ATTR_USER_ID, "dev-user-001");
                MDC.put("userId", "dev-user-001");
            }
            
            filterChain.doFilter(request, response);
            
        } finally {
            MDC.clear();
        }
    }
    
    private boolean isExcludedPath(String uri) {
        return uri.startsWith("/actuator") 
            || uri.equals("/api/v1/health")
            || uri.startsWith("/swagger-ui")
            || uri.startsWith("/api-docs")
            || uri.startsWith("/v3/api-docs");
    }
    
    private void extractAgentContext(HttpServletRequest request) {
        // First, check for explicit headers (from direct API calls)
        String agentId = request.getHeader(AGENT_ID_HEADER);
        String toolId = request.getHeader(TOOL_ID_HEADER);
        String sessionId = request.getHeader(SESSION_ID_HEADER);
        
        // If headers not present, try to extract from JWT token (Dialogflow CX)
        if (agentId == null || toolId == null || sessionId == null) {
            extractFromJwtToken(request);
        }
        
        // Now get final values (either from headers or JWT extraction)
        if (agentId != null) {
            request.setAttribute(ATTR_AGENT_ID, agentId);
            MDC.put("agentId", agentId);
        } else {
            // Check if JWT extraction set the attribute
            agentId = (String) request.getAttribute(ATTR_AGENT_ID);
            if (agentId != null) {
                MDC.put("agentId", agentId);
            }
        }
        
        if (toolId != null) {
            request.setAttribute(ATTR_TOOL_ID, toolId);
            MDC.put("toolId", toolId);
        } else {
            toolId = (String) request.getAttribute(ATTR_TOOL_ID);
            if (toolId != null) {
                MDC.put("toolId", toolId);
            }
        }
        
        if (sessionId != null) {
            request.setAttribute(ATTR_SESSION_ID, sessionId);
            MDC.put("sessionId", sessionId);
        } else {
            sessionId = (String) request.getAttribute(ATTR_SESSION_ID);
            if (sessionId != null) {
                MDC.put("sessionId", sessionId);
            }
        }
    }
    
    private void extractFromJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        
        String token = authHeader.substring(7);
        try {
            // JWT format: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return;
            }
            
            // Decode payload (base64url without padding)
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            
            log.debug("JWT payload for agent context extraction: {}", payload);
            
            // Extract agent info from email (Dialogflow CX uses "ces" service account)
            // Email format: "service-{PROJECT_ID}@gcp-sa-ces.iam.gserviceaccount.com"
            if (payload.contains("@gcp-sa-ces.iam.gserviceaccount.com")) {
                String agentId = "dialogflow-cx-agent";
                // Extract project ID from email for specific identification
                if (payload.contains("\"email\":\"service-")) {
                    int projectStart = payload.indexOf("\"email\":\"service-") + 17;
                    int projectEnd = payload.indexOf("@", projectStart);
                    if (projectEnd > projectStart) {
                        String projectId = payload.substring(projectStart, projectEnd);
                        agentId = "dialogflow-cx-" + projectId;
                    }
                }
                request.setAttribute(ATTR_AGENT_ID, agentId);
                log.debug("Extracted agent ID from JWT: {}", agentId);
            } else if (payload.contains("dialogflow") || payload.contains("agent")) {
                request.setAttribute(ATTR_AGENT_ID, "dialogflow-cx-agent");
                log.debug("Extracted agent ID from JWT: dialogflow-cx-agent");
            }
            
            // Extract tool from audience URL (aud field contains the endpoint path)
            if (payload.contains("\"aud\":\"")) {
                int audStart = payload.indexOf("\"aud\":\"") + 7;
                int audEnd = payload.indexOf("\"", audStart);
                if (audEnd > audStart) {
                    String aud = payload.substring(audStart, audEnd);
                    // aud contains full URL like "https://...run.app/api/v1/branches"
                    if (aud.contains("/branches")) {
                        request.setAttribute(ATTR_TOOL_ID, "searchBranches");
                        log.debug("Extracted tool ID from JWT aud: searchBranches");
                    } else if (aud.contains("/accounts")) {
                        request.setAttribute(ATTR_TOOL_ID, "accountOperations");
                        log.debug("Extracted tool ID from JWT aud: accountOperations");
                    }
                }
            }
            
            // Use JWT sub (service account ID) as session identifier
            if (payload.contains("\"sub\":\"")) {
                int subStart = payload.indexOf("\"sub\":\"") + 7;
                int subEnd = payload.indexOf("\"", subStart);
                if (subEnd > subStart) {
                    String sub = payload.substring(subStart, subEnd);
                    // Use a hash of sub to avoid logging full service account ID
                    String sessionId = "cx-" + Integer.toHexString(sub.hashCode());
                    request.setAttribute(ATTR_SESSION_ID, sessionId);
                    log.debug("Extracted session ID from JWT sub: {}", sessionId);
                }
            }
            
        } catch (Exception e) {
            log.warn("Could not extract agent context from JWT: {}", e.getMessage());
        }
    }
    
    private String validateAuthentication(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        
        String token = authHeader.substring(7);
        
        // TODO: Integrate with actual JWT validation (Spring Security or custom)
        // For now, accept any token and extract user ID from it
        // In production, this would validate signature, expiry, issuer, etc.
        
        if (token.isBlank()) {
            return null;
        }
        
        // Mock: extract user from token (in real impl, decode JWT)
        // Token format for development: "user-{userId}"
        if (token.startsWith("user-")) {
            return token;
        }
        
        // Accept any non-empty token in development
        return "authenticated-user";
    }
    
    private void sendUnauthorized(HttpServletResponse response, String correlationId) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format("""
            {
              "success": false,
              "error": {
                "code": "AUTHENTICATION_REQUIRED",
                "message": "Valid Authorization header required"
              },
              "meta": {
                "correlationId": "%s"
              }
            }
            """, correlationId));
    }
}
