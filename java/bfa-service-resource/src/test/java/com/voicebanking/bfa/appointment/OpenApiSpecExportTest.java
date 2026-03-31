package com.voicebanking.bfa.appointment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test that exports the Advisory Appointment OpenAPI spec to a file.
 *
 * <p>This test runs during every build and writes the generated OpenAPI 3.0
 * specification to {@code openapi-specs/advisory-appointment.json} at the
 * module root. This enables:</p>
 * <ul>
 *   <li>Version-controlled API contract tracking</li>
 *   <li>CI-based contract validation / breaking change detection</li>
 *   <li>Import into API gateways and documentation tools</li>
 * </ul>
 *
 * @author GitHub Copilot
 * @since 2026-03-15
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecExportTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExportAdvisoryAppointmentSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs/advisory-appointment"))
                .andExpect(status().isOk())
                .andReturn();

        String spec = result.getResponse().getContentAsString();
        assertThat(spec).isNotBlank();
        assertThat(spec).contains("\"title\":\"Acme Bank Advisory Appointment API\"");
        assertThat(spec).contains("/api/v1/appointment-taxonomy");
        assertThat(spec).contains("/api/v1/appointments");

        JsonNode root = OBJECT_MAPPER.readTree(spec);
        assertRequiredParameter(root, "/api/v1/appointment-service-search", "get", "query");
        assertRequiredParameter(root, "/api/v1/appointment-branches", "get", "entryPath");
        assertRequiredParameter(root, "/api/v1/appointment-branches", "get", "consultationChannel");
        assertRequiredParameter(root, "/api/v1/appointment-slots", "get", "entryPath");
        assertRequiredParameter(root, "/api/v1/appointment-slots", "get", "consultationChannel");
        assertRequiredParameter(root, "/api/v1/appointment-slots", "get", "locationId");
        assertRequiredParameter(root, "/api/v1/appointments/{appointmentId}", "get", "appointmentAccessToken");
        assertThat(containsParameter(root, "/api/v1/appointments/{appointmentId}", "get", "X-Appointment-Access-Token"))
                .isFalse();

        Path outputDir = Path.of(System.getProperty("user.dir"), "openapi-specs");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("advisory-appointment.json");
        Files.writeString(outputFile, spec);

        assertThat(outputFile).exists();
        assertThat(Files.size(outputFile)).isGreaterThan(100);

        System.out.println("✅ Advisory appointment OpenAPI spec exported to: " + outputFile.toAbsolutePath());
    }

    private void assertRequiredParameter(JsonNode root, String path, String method, String parameterName) {
        JsonNode parameter = findParameter(root, path, method, parameterName);
        assertThat(parameter)
                .as("Expected parameter '%s' in %s %s", parameterName, method.toUpperCase(), path)
                .isNotNull();
        assertThat(parameter.path("required").asBoolean(false))
                .as("Expected parameter '%s' to be required in %s %s", parameterName, method.toUpperCase(), path)
                .isTrue();
    }

    private boolean containsParameter(JsonNode root, String path, String method, String parameterName) {
        return findParameter(root, path, method, parameterName) != null;
    }

    private JsonNode findParameter(JsonNode root, String path, String method, String parameterName) {
        JsonNode parameters = root.path("paths").path(path).path(method).path("parameters");
        if (!parameters.isArray()) {
            return null;
        }
        for (JsonNode parameter : parameters) {
            if (parameterName.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return null;
    }
}
