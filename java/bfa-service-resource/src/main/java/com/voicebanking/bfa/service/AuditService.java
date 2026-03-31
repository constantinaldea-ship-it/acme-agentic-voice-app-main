package com.voicebanking.bfa.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for audit logging of BFA operations.
 * 
 * <p>All API operations are logged for compliance and security.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Service
public class AuditService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    
    @Value("${bfa.audit.enabled:true}")
    private boolean auditEnabled;
    
    @Value("${bfa.audit.async:true}")
    private boolean asyncLogging;
    
    /**
     * Log the start of a request.
     */
    public void logRequestStart(String correlationId, String userId, String agentId,
                                 String toolId, String sessionId,
                                 String operation, String method, String path) {
        if (!auditEnabled) return;
        
        if (asyncLogging) {
            logRequestStartAsync(correlationId, userId, agentId, toolId, sessionId,
                                  operation, method, path);
        } else {
            doLogRequestStart(correlationId, userId, agentId, toolId, sessionId,
                               operation, method, path);
        }
    }
    
    @Async
    protected void logRequestStartAsync(String correlationId, String userId, String agentId,
                                        String toolId, String sessionId,
                                        String operation, String method, String path) {
        doLogRequestStart(correlationId, userId, agentId, toolId, sessionId,
                           operation, method, path);
    }
    
    private void doLogRequestStart(String correlationId, String userId, String agentId,
                                   String toolId, String sessionId,
                                   String operation, String method, String path) {
        auditLog.info("REQUEST_START | corr={} | user={} | agent={} | tool={} | session={} | op={} | {} {}",
                      correlationId, userId, agentId, toolId, sessionId, operation, method, path);
    }
    
    /**
     * Log the completion of a request.
     */
    public void logRequestComplete(String correlationId, String userId, String agentId,
                                   String toolId, String sessionId,
                                   String operation, String outcome, int statusCode,
                                   long durationMs, String errorMessage) {
        if (!auditEnabled) return;
        
        if (asyncLogging) {
            logRequestCompleteAsync(correlationId, userId, agentId, toolId, sessionId,
                                    operation, outcome, statusCode, durationMs, errorMessage);
        } else {
            doLogRequestComplete(correlationId, userId, agentId, toolId, sessionId,
                                 operation, outcome, statusCode, durationMs, errorMessage);
        }
    }
    
    @Async
    protected void logRequestCompleteAsync(String correlationId, String userId, String agentId,
                                           String toolId, String sessionId,
                                           String operation, String outcome, int statusCode,
                                           long durationMs, String errorMessage) {
        doLogRequestComplete(correlationId, userId, agentId, toolId, sessionId,
                             operation, outcome, statusCode, durationMs, errorMessage);
    }
    
    private void doLogRequestComplete(String correlationId, String userId, String agentId,
                                      String toolId, String sessionId,
                                      String operation, String outcome, int statusCode,
                                      long durationMs, String errorMessage) {
        if (errorMessage != null) {
            auditLog.info("REQUEST_COMPLETE | corr={} | user={} | agent={} | tool={} | session={} | op={} | outcome={} | status={} | duration={}ms | error={}",
                          correlationId, userId, agentId, toolId, sessionId, operation, outcome, statusCode, durationMs, errorMessage);
        } else {
            auditLog.info("REQUEST_COMPLETE | corr={} | user={} | agent={} | tool={} | session={} | op={} | outcome={} | status={} | duration={}ms",
                          correlationId, userId, agentId, toolId, sessionId, operation, outcome, statusCode, durationMs);
        }
    }
    
    /**
     * Log a security event (authentication failure, authorization denied, etc.).
     */
    public void logSecurityEvent(String correlationId, String userId, String eventType, 
                                 String details) {
        if (!auditEnabled) return;
        
        auditLog.warn("SECURITY_EVENT | corr={} | user={} | type={} | details={}",
                      correlationId, userId, eventType, details);
    }
    
    /**
     * Audit record for structured logging.
     * 
     * <p>Includes all agent context headers for full traceability:</p>
     * <ul>
     *   <li>{@code agentId} - The calling agent (e.g., "ces-agent", "voice-banking-agent")</li>
     *   <li>{@code toolId} - The specific tool/operation within the agent</li>
     *   <li>{@code sessionId} - The conversation session identifier</li>
     * </ul>
     */
    public record AuditRecord(
        String correlationId,
        Instant timestamp,
        String userId,
        String agentId,
        String toolId,
        String sessionId,
        String operation,
        String method,
        String path,
        String outcome,
        int statusCode,
        long durationMs,
        String errorCode,
        String errorMessage
    ) {}
}
