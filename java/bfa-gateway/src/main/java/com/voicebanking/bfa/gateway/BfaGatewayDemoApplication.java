package com.voicebanking.bfa.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * BFA Gateway Demo — ADR-0104 Option C reference implementation.
 *
 * <p>Demonstrates the single-gateway + federated-adapter topology:
 * a {@code BfaGatewayController} receives tool invocation requests,
 * applies Edge PEP and Response PEP filters, delegates AuthZ to a
 * stub PDP service, and routes to the correct domain adapter action
 * via an {@code AdapterRegistry} using N-tools-to-1-adapter mapping.</p>
 *
 * <p>Configuration maps CES tool names to adapter + action pairs, then
 * resolves {@code POST {adapterBaseUrl}/actions/{action}} at runtime.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 * @see <a href="../../../../../../architecture/adrs/ADR-0104-backend-topology-single-bfa-gateway-tool-surface-internal-domain-adapters.md">ADR-0104</a>
 */
@SpringBootApplication
@EnableCaching
public class BfaGatewayDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BfaGatewayDemoApplication.class, args);
    }
}
