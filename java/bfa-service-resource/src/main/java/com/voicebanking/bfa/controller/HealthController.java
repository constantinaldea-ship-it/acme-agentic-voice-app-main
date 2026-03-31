package com.voicebanking.bfa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check controller.
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Service health endpoints")
public class HealthController {
    
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns service health status")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "bfa-service-resource",
            "timestamp", Instant.now().toString(),
            "version", "1.0.0"
        ));
    }
}
