package com.voicebanking.bfa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger configuration.
 * 
 * <p>Generates API documentation with security scheme and server info.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@Configuration
public class OpenApiConfig {
    
    @Value("${server.port:8082}")
    private int serverPort;
    
    @Bean
    public OpenAPI bfaOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("BFA Platform Services API")
                .description("""
                    Backend-for-Agents (BFA) platform services providing shared infrastructure 
                    capabilities for agent-based banking operations.
                    
                    ## Scope
                    This service contains platform-level features only:
                    - Authentication and authorization (Bearer JWT)
                    - Consent management and verification
                    - Legitimation (resource-level access control)
                    - Audit logging for all operations
                    - Location / branch finder services
                    - Health check endpoint
                    
                    ## Authentication
                    All endpoints require a Bearer token in the Authorization header.
                    Agent context can be passed via X-Agent-Id, X-Tool-Id, X-Session-Id headers.
                    
                    ## Security
                    - Consent requirements are declared per endpoint
                    - Resource access is verified via legitimation service
                    - All operations are audited
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Voice Banking Team")
                    .email("voice-banking@acmebank.example"))
                .license(new License()
                    .name("Internal Use Only")
                    .url("https://acmebank.example/licenses/internal")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local Development"),
                new Server()
                    .url("https://bfa.acmebank.example")
                    .description("Production")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT authentication token"))
                .addSecuritySchemes("agentContext", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-Agent-Id")
                    .description("Agent identifier for tracing")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
