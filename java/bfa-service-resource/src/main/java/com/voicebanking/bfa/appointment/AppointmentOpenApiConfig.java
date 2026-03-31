package com.voicebanking.bfa.appointment;

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
 * Standalone OpenAPI group for the advisory appointment API.
 *
 * @author Codex
 * @since 2026-03-15
 */
@Configuration
public class AppointmentOpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public GroupedOpenApi advisoryAppointmentApi() {
        return GroupedOpenApi.builder()
                .group("advisory-appointment")
                .displayName("Advisory Appointment Agent API")
                .pathsToMatch(
                        "/api/v1/appointment-taxonomy",
                        "/api/v1/appointment-service-search",
                        "/api/v1/appointment-branches",
                        "/api/v1/appointment-slots",
                        "/api/v1/appointments/**"
                )
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Acme Bank Advisory Appointment API")
                            .version("1.0.0")
                            .description("""
                                    Contract-first advisory appointment service for the Voice Banking Assistant.

                                    This Step 2 implementation introduces the resource package, OpenAPI group,
                                    and deterministic placeholder orchestration needed before the WireMock-backed
                                    mock-server fixtures arrive in Step 3.
                                    """)
                            .contact(new Contact()
                                    .name("Voice Banking Team")
                                    .email("voice-banking@acmebank.example"))
                            .license(new License()
                                    .name("Internal Use Only")
                                    .url("https://acmebank.example/licenses/internal")));

                    openApi.servers(List.of(
                            new Server().url("http://localhost:" + serverPort).description("Local Development"),
                            new Server().url("https://advisory-appointments.acmebank.example")
                                    .description("Production (future)")
                    ));

                    openApi.schemaRequirement("bearerAuth",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.HTTP)
                                    .scheme("bearer")
                                    .bearerFormat("JWT")
                                    .description("JWT token from Acme Bank authentication service."));
                    openApi.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                })
                .build();
    }
}
