package com.voicebanking.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone MCP server for the location services spike (ADR-CES-003).
 *
 * <p>This is a minimal Spring Boot application that exposes branch location
 * data via MCP Streamable HTTP transport. It runs independently from {@code bfa-service-resource}
 * to keep the spike cleanly separated from production code.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code /mcp} — MCP Streamable HTTP transport (CES-required)</li>
 *   <li>{@code /actuator/health} — Health check</li>
 * </ul>
 *
 * <h3>MCP Tools:</h3>
 * <ul>
 *   <li>{@code branch_search} — Search branches by city/address/GPS/brand</li>
 *   <li>{@code branch_details} — Get full branch details by ID</li>
 * </ul>
 *
 * @author Augment Agent
 * @since 2026-02-09
 * @see com.voicebanking.mcp.tools.LocationMcpTools
 */
@SpringBootApplication
public class McpSpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpSpikeApplication.class, args);
    }
}
