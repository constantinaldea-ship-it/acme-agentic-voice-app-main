package com.voicebanking.bfa.adapter.branchfinder;
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
 * Exports the branch-finder adapter OpenAPI specification to the module's openapi-specs folder.
 *
 * @author Copilot
 * @since 2026-03-27
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecExportTest {
    @Autowired
    private MockMvc mockMvc;
    @Test
    void shouldExportBranchFinderAdapterSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs/branch-finder-adapter"))
                .andExpect(status().isOk())
                .andReturn();
        String spec = result.getResponse().getContentAsString();
        assertThat(spec).isNotBlank();
        assertThat(spec).contains("\"title\":\"Acme Bank Branch Finder Adapter API\"");
        assertThat(spec).contains("/actions/search");
        assertThat(spec).contains("/health");
        Path outputDir = Path.of(System.getProperty("user.dir"), "openapi-specs");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("branch-finder-adapter.json");
        Files.writeString(outputFile, spec);
        assertThat(outputFile).exists();
        assertThat(Files.size(outputFile)).isGreaterThan(100);
    }
}
