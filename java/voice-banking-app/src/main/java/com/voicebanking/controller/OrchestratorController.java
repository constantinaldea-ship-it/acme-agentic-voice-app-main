package com.voicebanking.controller;

import com.voicebanking.domain.dto.OrchestratorRequest;
import com.voicebanking.domain.dto.OrchestratorResponse;
import com.voicebanking.service.OrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Orchestrator Controller
 * 
 * <p>Handles voice/text processing requests.</p>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // Allow React frontend on different port
public class OrchestratorController {
    
    private static final Logger log = LoggerFactory.getLogger(OrchestratorController.class);
    
    private final OrchestratorService orchestratorService;
    
    public OrchestratorController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }
    
    /**
     * Process voice/text request
     * 
     * POST /api/orchestrate
     * 
     * @param request Orchestrator request
     * @return Orchestrator response
     */
    @PostMapping("/orchestrate")
    public ResponseEntity<OrchestratorResponse> orchestrate(
            @Valid @RequestBody OrchestratorRequest request) {
        
        log.info("POST /api/orchestrate - sessionId: {}", request.sessionId());
        
        try {
            OrchestratorResponse response = orchestratorService.process(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Orchestration failed", e);
            return ResponseEntity.internalServerError()
                .body(OrchestratorResponse.refusal(
                    request.text() != null ? request.text() : "",
                    "server_error",
                    "An internal error occurred: " + e.getMessage()
                ));
        }
    }
}
