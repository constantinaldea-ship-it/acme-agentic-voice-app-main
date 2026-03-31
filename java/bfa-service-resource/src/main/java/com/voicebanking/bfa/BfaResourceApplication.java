package com.voicebanking.bfa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * BFA Platform Services Application.
 * 
 * <p>Provides shared platform infrastructure for agent-based banking operations,
 * including authentication, authorization, audit, consent management,
 * and location/branch finder services.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Authentication and authorization (Bearer JWT)</li>
 *   <li>Consent management and verification</li>
 *   <li>Legitimation (resource-level access control)</li>
 *   <li>Audit logging</li>
 *   <li>Location / branch finder REST endpoints</li>
 *   <li>OpenAPI documentation auto-generated</li>
 * </ul>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 * @see <a href="../docs/architecture/bfa/adr/ADR-BFA-002-resource-oriented-rest.md">ADR-BFA-002</a>
 */
@SpringBootApplication
@EnableCaching
public class BfaResourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BfaResourceApplication.class, args);
    }
}
