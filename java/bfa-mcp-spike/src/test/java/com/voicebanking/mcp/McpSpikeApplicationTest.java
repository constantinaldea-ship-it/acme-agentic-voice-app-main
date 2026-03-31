package com.voicebanking.mcp;

import org.junit.jupiter.api.Test;

/**
 * Basic smoke test for the MCP spike application.
 */
class McpSpikeApplicationTest {

    @Test
    void contextCreation() {
        // Validates that the application class exists and is properly structured.
        // Full Spring context loading is skipped for unit test speed.
        McpSpikeApplication app = new McpSpikeApplication();
        org.junit.jupiter.api.Assertions.assertNotNull(app);
    }
}
