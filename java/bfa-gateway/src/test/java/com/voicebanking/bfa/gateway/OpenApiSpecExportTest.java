package com.voicebanking.bfa.gateway;
import com.voicebanking.bfa.gateway.adapter.AdapterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * Exports the BFA Gateway OpenAPI specification to the module's openapi-specs folder.
 *
 * @author Copilot
 * @since 2026-03-27
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecExportTest {
    @Autowired
    private MockMvc mockMvc;
    @SuppressWarnings("unused")
    @MockBean
    private AdapterRegistry adapterRegistry;
    @Test
    void shouldExportGatewaySpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api-docs/bfa-gateway"))
                .andExpect(status().isOk())
                .andReturn();
        String spec = result.getResponse().getContentAsString();
        assertThat(spec).isNotBlank();
        assertThat(spec).contains("\"title\":\"Acme Bank BFA Gateway API\"");
        assertThat(spec).contains("/api/v1/tools/invoke");
        assertThat(spec).contains("/api/v1/tools");
        Path outputDir = Path.of(System.getProperty("user.dir"), "openapi-specs");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("bfa-gateway.json");
        Files.writeString(outputFile, spec);
        assertThat(outputFile).exists();
        assertThat(Files.size(outputFile)).isGreaterThan(100);
    }
}
