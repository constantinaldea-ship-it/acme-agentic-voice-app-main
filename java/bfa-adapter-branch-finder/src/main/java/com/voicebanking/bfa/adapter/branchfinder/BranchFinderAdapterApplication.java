package com.voicebanking.bfa.adapter.branchfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Branch Finder Adapter (AG-003) — separately deployable domain adapter.
 *
 * <p>This is an independent Spring Boot service per ADR-0104 Option C.
 * It runs on its own port, has its own release lifecycle, and is called
 * by the BFA Gateway over HTTP — not in-process.</p>
 *
 * <p>In production this would be deployed as a Cloud Run internal service
 * (per ADR-0105), reachable only from the BFA Gateway via VPC-SC.</p>
 *
 * @author Copilot
 * @since 2026-03-01
 */
@SpringBootApplication
public class BranchFinderAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(BranchFinderAdapterApplication.class, args);
    }
}
