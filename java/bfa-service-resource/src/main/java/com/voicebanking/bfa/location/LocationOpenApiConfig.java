package com.voicebanking.bfa.location;

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
 * OpenAPI configuration for the Location Services Agent API.
 *
 * <p>Generates a standalone, per-agent OpenAPI 3.0 specification at
 * {@code /api-docs/location-services}. This contract is independently
 * deployable to Cloud Run, API Gateway, or any OpenAPI-consuming infrastructure.</p>
 *
 * <p>The generated spec is automatically exported to {@code openapi-specs/location-services.json}
 * by the {@code OpenApiSpecExportTest} during every build.</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@Configuration
public class LocationOpenApiConfig {

    @Value("${server.port:8082}")
    private int serverPort;

    @Bean
    public GroupedOpenApi locationServicesApi() {
        return GroupedOpenApi.builder()
                .group("location-services")
                .displayName("Location Services Agent API")
                .pathsToMatch("/api/v1/branches/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Acme Bank Location Services API")
                            .version("1.0.0")
                            .description("""
                                    Branch and ATM location search service for the Voice Banking Assistant.
                                    
                                    Provides distance-sorted branch search by city, address, postal code,
                                    or GPS coordinates. When no explicit coordinates are provided, city
                                    centroids (derived from the branch data itself) are used as the
                                    reference point for distance sorting.
                                    
                                    This API is designed to be consumed by:
                                    - AI agents (LLM tool calls via ADK/Langchain)
                                    - Voice banking orchestration services (Dialogflow CX webhooks)
                                    - Mobile and web frontends
                                    
                                    Data: 238 branches (Deutsche Bank + Postbank) across 50 German cities.
                                    Data source: Deutsche Bank Filialfinder API.""")
                            .contact(new Contact()
                                    .name("Voice Banking Team")
                                    .email("voice-banking@acmebank.example"))
                            .license(new License()
                                    .name("Internal Use Only")
                                    .url("https://acmebank.example/licenses/internal")));

                    openApi.servers(List.of(
                            new Server()
                                    .url("http://localhost:" + serverPort)
                                    .description("Local Development"),
                            new Server()
                                    .url("https://location-services.acmebank.example")
                                    .description("Production (Cloud Run)")));

                    // JWT Bearer authentication scheme
                    openApi.schemaRequirement("bearerAuth",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT")
                                    .description("JWT token from Acme Bank authentication service. "
                                            + "Required header: Authorization: Bearer <token>"));
                    openApi.addSecurityItem(
                            new SecurityRequirement().addList("bearerAuth"));
                })
                .build();
    }
}
