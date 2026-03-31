package com.acme.banking.demoaccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Exports a canonical OpenAPI specification for the WireMock-driven mock-server.
 *
 * @author Copilot
 * @since 2026-03-27
 */
class OpenApiSpecExportTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SPEC = """
            {
              "openapi": "3.0.1",
              "info": {
                "title": "Acme Bank Mock Server API",
                "version": "1.0.0",
                "description": "Canonical contract for the mock-server Cloud Run service. Covers authentication, customer details, and advisory appointment upstream endpoints."
              },
              "servers": [
                {
                  "url": "http://localhost:8080",
                  "description": "Local Development"
                },
                {
                  "url": "https://mock-server.acmebank.example",
                  "description": "Production (Cloud Run)"
                }
              ],
              "paths": {
                "/oauth/token": {
                  "post": {
                    "operationId": "getEidpToken",
                    "summary": "Obtain EIDP access token",
                    "parameters": [
                      {"name": "X-Agent-Id", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "X-Tool-Id", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "X-Debug-Echo-Headers", "in": "header", "required": false, "schema": {"type": "string"}}
                    ],
                    "responses": {"200": {"description": "Token issued"}, "401": {"description": "Invalid client credentials"}}
                  }
                },
                "/authz/authorize": {
                  "post": {
                    "operationId": "getAuthzToken",
                    "summary": "Exchange EIDP token for AuthZ token",
                    "parameters": [
                      {"name": "X-Agent-Id", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "X-Tool-Id", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "X-Debug-Echo-Headers", "in": "header", "required": false, "schema": {"type": "string"}}
                    ],
                    "responses": {"200": {"description": "Authorization granted"}, "401": {"description": "Missing or invalid EIDP token"}, "403": {"description": "Authorization denied"}}
                  }
                },
                "/customers/{partnerId}/personal-data": {
                  "get": {
                    "operationId": "getCustomerPersonalData",
                    "summary": "Get customer personal data",
                    "parameters": [
                      {"name": "partnerId", "in": "path", "required": true, "schema": {"type": "string"}},
                      {"name": "X-Agent-Id", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "X-Tool-Id", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "X-Debug-Echo-Headers", "in": "header", "required": false, "schema": {"type": "string"}},
                      {"name": "deuba-client-id", "in": "header", "required": true, "schema": {"type": "string"}},
                      {"name": "DB-ID", "in": "header", "required": true, "schema": {"type": "string"}},
                      {"name": "Authorization", "in": "header", "required": true, "schema": {"type": "string"}}
                    ],
                    "responses": {"200": {"description": "Customer details returned"}, "401": {"description": "Missing or invalid authorization"}, "403": {"description": "Access denied"}, "404": {"description": "Customer not found"}}
                  }
                },
                "/advisory-appointments/taxonomy": {
                  "get": {
                    "operationId": "getAppointmentTaxonomyUpstream",
                    "summary": "Get advisory appointment taxonomy",
                    "responses": {"200": {"description": "Taxonomy returned"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                },
                "/advisory-appointments/service-search": {
                  "get": {
                    "operationId": "searchAppointmentServicesUpstream",
                    "summary": "Search advisory appointment services",
                    "responses": {"200": {"description": "Matches returned"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                },
                "/advisory-appointments/eligibility": {
                  "get": {
                    "operationId": "searchAppointmentEligibilityUpstream",
                    "summary": "Get booking-eligible appointment locations",
                    "responses": {"200": {"description": "Eligibility returned"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                },
                "/advisory-appointments/availability": {
                  "get": {
                    "operationId": "getAppointmentAvailabilityUpstream",
                    "summary": "Get available appointment days and slots",
                    "responses": {"200": {"description": "Availability returned"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                },
                "/advisory-appointments/lifecycle": {
                  "post": {
                    "operationId": "createAppointmentUpstream",
                    "summary": "Create advisory appointment upstream",
                    "responses": {"200": {"description": "Appointment created"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                },
                "/advisory-appointments/lifecycle/{appointmentId}": {
                  "get": {
                    "operationId": "getAppointmentUpstream",
                    "summary": "Get advisory appointment upstream",
                    "parameters": [
                      {"name": "appointmentId", "in": "path", "required": true, "schema": {"type": "string"}},
                      {"name": "appointmentAccessToken", "in": "query", "required": true, "schema": {"type": "string"}}
                    ],
                    "responses": {"200": {"description": "Appointment returned"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}, "404": {"description": "Appointment not found"}}
                  }
                },
                "/advisory-appointments/lifecycle/{appointmentId}/cancel": {
                  "post": {
                    "operationId": "cancelAppointmentUpstream",
                    "summary": "Cancel advisory appointment upstream",
                    "parameters": [
                      {"name": "appointmentId", "in": "path", "required": true, "schema": {"type": "string"}}
                    ],
                    "responses": {"200": {"description": "Appointment cancelled"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                },
                "/advisory-appointments/lifecycle/{appointmentId}/reschedule": {
                  "post": {
                    "operationId": "rescheduleAppointmentUpstream",
                    "summary": "Reschedule advisory appointment upstream",
                    "parameters": [
                      {"name": "appointmentId", "in": "path", "required": true, "schema": {"type": "string"}}
                    ],
                    "responses": {"200": {"description": "Appointment rescheduled"}, "401": {"description": "Missing authorization"}, "403": {"description": "Missing client header"}}
                  }
                }
              }
            }
            """;
    @Test
    void shouldExportMockServerSpec() throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(SPEC);
        assertThat(root.path("info").path("title").asText()).isEqualTo("Acme Bank Mock Server API");
        assertThat(root.path("paths").has("/oauth/token")).isTrue();
        assertThat(root.path("paths").has("/authz/authorize")).isTrue();
        assertThat(root.path("paths").has("/customers/{partnerId}/personal-data")).isTrue();
        assertThat(root.path("paths").has("/advisory-appointments/taxonomy")).isTrue();
        assertThat(root.path("paths").has("/advisory-appointments/lifecycle/{appointmentId}/reschedule")).isTrue();
        Path outputDir = Path.of(System.getProperty("user.dir"), "openapi-specs");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("mock-server.json");
        Files.writeString(outputFile, SPEC + System.lineSeparator());
        assertThat(outputFile).exists();
        assertThat(Files.size(outputFile)).isGreaterThan(100);
    }
}
