package com.acme.banking.demoaccount;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the Demo Account Mock Server.
 * <p>
 * This Spring Boot application starts an embedded WireMock server
 * that serves mock responses for the Demo Account API.
 * </p>
 */
@SpringBootApplication
public class DemoAccountApplication {

    private static final Logger logger = LoggerFactory.getLogger(DemoAccountApplication.class);

    @Value("${wiremock.server.port:8080}")
    private int wireMockPort;

    @Value("${wiremock.root.dir:}")
    private String wireMockRootDir;

    @Value("${mock.api.key:}")
    private String mockApiKey;

    private WireMockServer wireMockServer;

    public static void main(String[] args) {
        SpringApplication.run(DemoAccountApplication.class, args);
    }

    /**
     * Creates and configures the WireMock server bean.
     * WireMock runs on the same port as the server for Cloud Run compatibility.
     *
     * @return configured WireMockServer instance
     */
    @Bean
    public WireMockServer wireMockServer() {
        WireMockConfiguration config = WireMockConfiguration.options()
                .port(wireMockPort)
                .globalTemplating(true);

        // Use file system path if provided, otherwise use classpath
        if (wireMockRootDir != null && !wireMockRootDir.isEmpty()) {
            config.usingFilesUnderDirectory(wireMockRootDir);
            logger.info("Loading WireMock files from directory: {}", wireMockRootDir);
        } else {
            config.usingFilesUnderClasspath("wiremock");
            logger.info("Loading WireMock files from classpath");
        }

        // Register API key filter
        config.extensions(
                new ApiKeyRequestFilter(mockApiKey),
                new HeaderCaptureResponseTransformer()
        );
        if (mockApiKey != null && !mockApiKey.isBlank()) {
            logger.info("API key protection ENABLED (X-API-Key header required)");
        } else {
            logger.info("API key protection DISABLED (no MOCK_API_KEY set)");
        }

        wireMockServer = new WireMockServer(config);
        return wireMockServer;
    }

    /**
     * Starts the WireMock server after the application context is initialized.
     */
    @Bean
    public CommandLineRunner startWireMock(WireMockServer wireMockServer) {
        return args -> {
            wireMockServer.start();
            logger.info("========================================");
            logger.info("WireMock server started on port: {}", wireMockPort);
            logger.info("Mappings loaded from classpath:/wiremock");
            logger.info("========================================");
            logger.info("Available endpoints:");
            logger.info("  POST /oauth/token                          - EIDP token (client_credentials)");
            logger.info("  POST /authz/authorize                      - AuthZ authorization token");
            logger.info("  GET  /customers/{partnerId}/personal-data  - Customer personal data");
            logger.info("Required headers for customer endpoints:");
            logger.info("  deuba-client-id (must contain '-banking')");
            logger.info("  DB-ID (any non-empty value)");
            logger.info("  Authorization: Bearer <authz_token>");
            logger.info("  X-API-Key: <api-key> (all endpoints)");
            logger.info("========================================");
        };
    }

    /**
     * Stops the WireMock server when the application context is destroyed.
     */
    @PreDestroy
    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            logger.info("WireMock server stopped");
        }
    }
}
