package com.voicebanking.bfa.adapter.branchfinder.config;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
/**
 * OpenAPI configuration for the branch-finder adapter service.
 *
 * @author Copilot
 * @since 2026-03-27
 */
@Configuration
public class BranchFinderOpenApiConfig {
    @Value("${server.port:8082}")
    private int serverPort;
    @Bean
    public GroupedOpenApi branchFinderAdapterApi() {
        return GroupedOpenApi.builder()
                .group("branch-finder-adapter")
                .displayName("Branch Finder Adapter API")
                .pathsToMatch("/actions/**", "/health")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Acme Bank Branch Finder Adapter API")
                            .version("1.0.0")
                            .description("""
                                    Domain adapter contract for AG-003 branch-finder.
                                    This export is the canonical module contract for the
                                    bfa-adapter-branch-finder Cloud Run service and is
                                    written to openapi-specs/branch-finder-adapter.json
                                    during test runs.
                                    """)
                            .contact(new Contact()
                                    .name("Voice Banking Team")
                                    .email("voice-banking@acmebank.example"))
                            .license(new License()
                                    .name("Internal Use Only")
                                    .url("https://acmebank.example/licenses/internal")));
                    openApi.servers(List.of(
                            new Server().url("http://localhost:" + serverPort).description("Local Development"),
                            new Server().url("https://branch-finder-adapter.acmebank.example")
                                    .description("Production (Cloud Run)")));
                })
                .build();
    }
}
