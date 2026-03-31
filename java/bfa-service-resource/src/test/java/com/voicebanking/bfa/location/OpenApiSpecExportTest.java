package com.voicebanking.bfa.location;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test that exports the Location Services OpenAPI spec to a file.
 *
 * <p>This test runs during every build and writes the generated OpenAPI 3.0
 * specification to {@code openapi-specs/location-services.json} at the
 * module root. This enables:</p>
 * <ul>
 *   <li>Version-controlled API contract tracking</li>
 *   <li>CI-based contract validation / breaking change detection</li>
 *   <li>Import into API gateways and documentation tools</li>
 * </ul>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExportLocationServicesSpec() throws Exception {
        // Fetch the OpenAPI spec from the running app
        MvcResult result = mockMvc.perform(get("/api-docs/location-services"))
                .andExpect(status().isOk())
                .andReturn();

        String spec = result.getResponse().getContentAsString();
        assertThat(spec).isNotBlank();
        assertThat(spec).contains("\"title\":\"Acme Bank Location Services API\"");
        assertThat(spec).contains("/api/v1/branches");

        // Write to module root's openapi-specs/ directory
        Path outputDir = Path.of(System.getProperty("user.dir"), "openapi-specs");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("location-services.json");
        Files.writeString(outputFile, spec);

        assertThat(outputFile).exists();
        assertThat(Files.size(outputFile)).isGreaterThan(100);

        System.out.println("✅ OpenAPI spec exported to: " + outputFile.toAbsolutePath());
    }

    @Test
    void shouldExportDefaultApiSpec() throws Exception {
        // Also export the default (combined) API spec
        MvcResult result = mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String spec = result.getResponse().getContentAsString();
        assertThat(spec).isNotBlank();

        Path outputDir = Path.of(System.getProperty("user.dir"), "openapi-specs");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("bfa-resource-all.json");
        Files.writeString(outputFile, spec);

        assertThat(outputFile).exists();
        System.out.println("✅ Combined OpenAPI spec exported to: " + outputFile.toAbsolutePath());
    }
}
