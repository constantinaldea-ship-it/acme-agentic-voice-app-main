package com.voicebanking.bfa.gateway.config;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI configuration for the BFA Gateway service.
 *
 * @author Copilot
 * @since 2026-03-27
 */
@Configuration
public class GatewayOpenApiConfig {

    @Value("${server.port:8081}")
    private int serverPort;

    @Bean
    public GroupedOpenApi gatewayApi() {
        return GroupedOpenApi.builder()
                .group("bfa-gateway")
                .displayName("BFA Gateway API")
                .pathsToMatch("/api/v1/tools/**", "/api/v1/health")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Acme Bank BFA Gateway API")
                            .version("1.0.0")
                            .description("""
                                    Single-ingress gateway for CES-facing tool invocation.

                                    This export is the canonical module contract for the
                                    bfa-gateway Cloud Run service and is written to
                                    openapi-specs/bfa-gateway.json during test runs.
                                    """)
                            .contact(new Contact()
                                    .name("Voice Banking Team")
                                    .email("voice-banking@acmebank.example"))
                            .license(new License()
                                    .name("Internal Use Only")
                                    .url("https://acmebank.example/licenses/internal")));

                    openApi.servers(List.of(
                            new Server().url("http://localhost:" + serverPort).description("Local Development"),
                            new Server().url("https://bfa-gateway.acmebank.example").description("Production (Cloud Run)")));

                    openApi.schemaRequirement("bearerAuth",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT")
                                    .description("Bearer token supplied by CES or an upstream orchestrator."));
                    openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                })
                .build();
    }
}

