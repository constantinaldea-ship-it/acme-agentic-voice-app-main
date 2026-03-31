package com.voicebanking.bfa.gateway.adapter;

import com.voicebanking.bfa.gateway.auth.CloudRunIdTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Action-routed adapter registry (ADR-0104 Option C).
 *
 * <p>Routes CES tool calls to domain adapters using a two-level mapping:
 * <ol>
 *   <li><strong>Tool mapping:</strong> CES {@code toolName} → adapter name + action</li>
 *   <li><strong>Adapter addressing:</strong> adapter name → base URL</li>
 * </ol>
 *
 * <p>This supports the N-tools-to-1-adapter pattern required by multi-operation
 * domain adapters like AG-004 (Appointments), where multiple CES tools
 * ({@code searchPartners}, {@code findAdvisors}, {@code getAppointmentSlots},
 * {@code createAppointment}) all route to the same adapter service.</p>
 *
 * <h3>Configuration</h3>
 * <pre>
 *   # Tool → Adapter + Action mapping
 *   bfa.gateway.tools.searchPartners.adapter=appointments
 *   bfa.gateway.tools.searchPartners.action=search-partners
 *
 *   bfa.gateway.tools.branchFinder.adapter=branch-finder
 *   bfa.gateway.tools.branchFinder.action=search
 *
 *   # Adapter base URLs
 *   bfa.gateway.adapters.appointments.url=http://localhost:8083
 *   bfa.gateway.adapters.appointments.description=AG-004 — Appointments
 *
 *   bfa.gateway.adapters.branch-finder.url=http://localhost:8082
 *   bfa.gateway.adapters.branch-finder.description=AG-003 — Branch Finder
 * </pre>
 *
 * <p>The gateway resolves: {@code toolName} → {@code adapter + action} →
 * {@code POST {adapterBaseUrl}/actions/{action}}</p>
 *
 * @author Copilot
 * @since 2026-01-17
 * @modified Copilot on 2026-03-02 — refactored to action-routed N:1 tool-to-adapter mapping
 * @see RegistryProperties
 */
@Component
public class AdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(AdapterRegistry.class);

    private final Map<String, ToolRoute> toolRoutes;
    private final Map<String, AdapterEndpoint> adapters;
    private final RestClient restClient;
    private final CloudRunIdTokenService cloudRunIdTokenService;

    public AdapterRegistry(RegistryProperties properties,
                           RestClient.Builder restClientBuilder,
                           CloudRunIdTokenService cloudRunIdTokenService) {
        this.restClient = restClientBuilder.build();
        this.cloudRunIdTokenService = cloudRunIdTokenService;
        this.toolRoutes = new LinkedHashMap<>();
        this.adapters = new LinkedHashMap<>();

        // Register adapter base URLs
        if (properties.getAdapters() != null) {
            properties.getAdapters().forEach((adapterName, config) -> {
                adapters.put(adapterName, new AdapterEndpoint(
                        adapterName, config.getUrl(), config.getDescription()));
                log.info("Registered adapter: name='{}' url='{}' — {}",
                        adapterName, config.getUrl(), config.getDescription());
            });
        }

        // Register tool → adapter+action mappings
        if (properties.getTools() != null) {
            properties.getTools().forEach((toolName, config) -> {
                if (!adapters.containsKey(config.getAdapter())) {
                    log.warn("Tool '{}' references unknown adapter '{}' — skipping",
                            toolName, config.getAdapter());
                    return;
                }
                AdapterEndpoint endpoint = adapters.get(config.getAdapter());
                String actionUrl = endpoint.url() + "/actions/" + config.getAction();
                toolRoutes.put(toolName, new ToolRoute(
                        toolName, config.getAdapter(), config.getAction(), actionUrl));
                log.info("Registered tool route: tool='{}' → adapter='{}' action='{}' url='{}'",
                        toolName, config.getAdapter(), config.getAction(), actionUrl);
            });
        }

        log.info("AdapterRegistry initialised: {} adapter(s) {}, {} tool route(s) {}",
                adapters.size(), adapters.keySet(),
                toolRoutes.size(), toolRoutes.keySet());
    }

    /**
     * Check whether a tool name has a registered route.
     */
    public boolean hasAdapter(String toolName) {
        return toolRoutes.containsKey(toolName);
    }

    /**
     * Invoke a remote adapter action over HTTP.
     *
     * <p>Resolves {@code toolName} → adapter + action, then calls
     * {@code POST {adapterBaseUrl}/actions/{action}} with the standard
     * adapter request envelope.</p>
     *
     * @param toolName       the CES tool identifier
     * @param parameters     tool-specific parameters
     * @param correlationId  trace correlation ID
     * @return the adapter's response as a raw map
     * @throws AdapterNotFoundException    if no tool route is registered
     * @throws AdapterInvocationException  on HTTP or connectivity errors
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> invoke(String toolName,
                                       Map<String, Object> parameters,
                                       String correlationId) {
        ToolRoute route = toolRoutes.get(toolName);
        if (route == null) {
            throw new AdapterNotFoundException(toolName, registeredTools());
        }

        Map<String, Object> requestBody = Map.of(
                "correlationId", correlationId,
                "parameters", parameters != null ? parameters : Map.of()
        );

        log.debug("[{}] Invoking adapter: tool='{}' adapter='{}' action='{}' url='{}'",
                correlationId, toolName, route.adapterName(), route.action(), route.actionUrl());

        try {
            Map<String, Object> response = restClient.post()
                    .uri(route.actionUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> cloudRunIdTokenService
                        .serverlessAuthorizationHeaderValue(route.actionUrl())
                        .ifPresent(value -> {
                            headers.setBearerAuth(value.replaceFirst("^Bearer\\s+", ""));
                            headers.set(CloudRunIdTokenService.SERVERLESS_AUTHORIZATION_HEADER, value);
                        }))
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new AdapterInvocationException(toolName,
                        "Adapter returned null response");
            }

            return response;

        } catch (HttpClientErrorException e) {
            log.warn("[{}] Adapter '{}' action '{}' returned HTTP {}: {}",
                    correlationId, route.adapterName(), route.action(),
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdapterInvocationException(toolName,
                    "Adapter returned HTTP %s".formatted(e.getStatusCode()), e);
        } catch (ResourceAccessException e) {
            log.error("[{}] Cannot reach adapter '{}' at {}: {}",
                    correlationId, route.adapterName(), route.actionUrl(), e.getMessage());
            throw new AdapterInvocationException(toolName,
                    "Adapter unreachable at %s".formatted(route.actionUrl()), e);
        }
    }

    /**
     * List all registered tool names (for health / discovery endpoints).
     */
    public List<String> registeredTools() {
        return toolRoutes.keySet().stream().sorted().collect(Collectors.toUnmodifiableList());
    }

    /**
     * List all registered adapter names (for health / discovery endpoints).
     */
    public List<String> registeredAdapters() {
        return adapters.keySet().stream().sorted().collect(Collectors.toUnmodifiableList());
    }

    // ── inner types ─────────────────────────────────────────────────

    /** Resolved route: tool → adapter + action + full URL. */
    public record ToolRoute(String toolName, String adapterName, String action, String actionUrl) {}

    /** Adapter base endpoint (name + URL + description). */
    public record AdapterEndpoint(String name, String url, String description) {}

    /** Thrown when no tool route is registered for a given tool name. */
    public static class AdapterNotFoundException extends RuntimeException {
        private final String toolName;
        private final List<String> availableTools;

        public AdapterNotFoundException(String toolName, List<String> availableTools) {
            super("No tool route registered for '%s'. Available tools: %s"
                    .formatted(toolName, availableTools));
            this.toolName = toolName;
            this.availableTools = availableTools;
        }

        public String getToolName() { return toolName; }
        public List<String> getAvailableTools() { return availableTools; }
    }

    /** Thrown when the HTTP call to a remote adapter fails. */
    public static class AdapterInvocationException extends RuntimeException {
        private final String toolName;

        public AdapterInvocationException(String toolName, String message) {
            super(message);
            this.toolName = toolName;
        }

        public AdapterInvocationException(String toolName, String message, Throwable cause) {
            super(message, cause);
            this.toolName = toolName;
        }

        public String getToolName() { return toolName; }
    }

    // ── configuration properties bean ───────────────────────────────

    @Component
    @ConfigurationProperties(prefix = "bfa.gateway")
    public static class RegistryProperties {

        private Map<String, AdapterConfig> adapters;
        private Map<String, ToolConfig> tools;

        public Map<String, AdapterConfig> getAdapters() { return adapters; }
        public void setAdapters(Map<String, AdapterConfig> adapters) { this.adapters = adapters; }

        public Map<String, ToolConfig> getTools() { return tools; }
        public void setTools(Map<String, ToolConfig> tools) { this.tools = tools; }

        /** Adapter base URL + description. */
        public static class AdapterConfig {
            private String url;
            private String description = "";

            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }
        }

        /** Tool → adapter + action mapping. */
        public static class ToolConfig {
            private String adapter;
            private String action;

            public String getAdapter() { return adapter; }
            public void setAdapter(String adapter) { this.adapter = adapter; }
            public String getAction() { return action; }
            public void setAction(String action) { this.action = action; }
        }
    }
}
