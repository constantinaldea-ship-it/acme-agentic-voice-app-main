package com.voicebanking.bfa.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.bfa.gateway.dto.ErrorResponse;
import com.voicebanking.bfa.gateway.dto.ToolInvokeResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Edge PEP (Policy Enforcement Point) — runs <em>before</em> Spring MVC.
 *
 * <p>Responsibilities per ADR-0104:</p>
 * <ol>
 *   <li>Generate / propagate a correlation ID ({@code X-Correlation-ID}).</li>
 *   <li>Verify the presence of a {@code Bearer} token in the
 *       {@code Authorization} header.  Reject with 401 if missing.</li>
 *   <li>Extract the principal identity and store it as a request attribute
 *       for downstream use by the controller and AuthZ PDP.</li>
 * </ol>
 *
 * <p>Token <em>validation</em> is intentionally stubbed: any non-blank
 * bearer value is accepted.  In production, this filter would delegate to
 * a JWT validation library.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 */
@Component
@Order(1)
public class EdgePepFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EdgePepFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String ATTR_PRINCIPAL = "bfa.principal";
    public static final String ATTR_CORRELATION_ID = "bfa.correlationId";

    private final boolean securityEnabled;
    private final ObjectMapper objectMapper;

    public EdgePepFilter(
            @Value("${bfa.gateway.security.enabled:true}") boolean securityEnabled,
            ObjectMapper objectMapper) {
        this.securityEnabled = securityEnabled;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        // ── Correlation ID ──
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        request.setAttribute(ATTR_CORRELATION_ID, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            // Skip auth for health/actuator/swagger endpoints
            if (isExcludedPath(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }

            if (securityEnabled) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    rejectUnauthorised(response, correlationId);
                    return;
                }
                // Stub: extract principal from token (in production → JWT decode)
                String token = authHeader.substring(7).trim();
                if (token.isBlank()) {
                    rejectUnauthorised(response, correlationId);
                    return;
                }
                request.setAttribute(ATTR_PRINCIPAL, token);
                log.debug("[{}] Edge PEP — authenticated principal: {}", correlationId, token);
            } else {
                request.setAttribute(ATTR_PRINCIPAL, "anonymous");
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private boolean isExcludedPath(String uri) {
        return uri.startsWith("/actuator")
                || uri.startsWith("/api-docs")
                || uri.startsWith("/swagger-ui")
                || uri.equals("/api/v1/health");
    }

    private void rejectUnauthorised(HttpServletResponse response, String correlationId)
            throws IOException {
        log.warn("[{}] Edge PEP — rejected: missing or invalid Bearer token", correlationId);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ToolInvokeResponse body = ToolInvokeResponse.failure(
                null,
                ErrorResponse.of("AUTHENTICATION_REQUIRED",
                        "A valid Bearer token is required in the Authorization header"),
                correlationId);

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
