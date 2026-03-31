package com.voicebanking.controller;

import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * Health Controller
 * 
 * <p>Health check and status endpoints.</p>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HealthController {
    
    private final Environment environment;
    
    public HealthController(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * Health check endpoint
     * 
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String[] profiles = environment.getActiveProfiles();
        String profileStr = profiles.length > 0 ? String.join(",", profiles) : "default";
        
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "timestamp", Instant.now().toString(),
            "profile", profileStr,
            "service", "voice-banking-assistant"
        ));
    }
}
