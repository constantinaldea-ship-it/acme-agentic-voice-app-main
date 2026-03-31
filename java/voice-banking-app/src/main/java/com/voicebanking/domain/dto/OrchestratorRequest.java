package com.voicebanking.domain.dto;

import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Orchestrator Request DTO
 * 
 * <p>Input to the orchestrator. Must include either audio or text.</p>
 * 
 * @param audio Base64-encoded audio data (optional, mutually exclusive with text)
 * @param text Text input (optional, mutually exclusive with audio)
 * @param sessionId Session identifier (UUID recommended)
 * @param consentAccepted Whether user accepted voice processing consent
 */
public record OrchestratorRequest(
    String audio,
    String text,
    
    @NotBlank(message = "Session ID is required")
    @Size(max = 36, message = "Session ID must not exceed 36 characters")
    String sessionId,
    
    boolean consentAccepted
) {
    /**
     * Yavi Validator for cross-field validation
     */
    private static final Validator<OrchestratorRequest> VALIDATOR = ValidatorBuilder
        .<OrchestratorRequest>of()
        .constraint(OrchestratorRequest::sessionId, "sessionId",
            c -> c.notBlank().lessThanOrEqual(36))
        .constraintOnCondition(
            (req, group) -> req.audio != null && req.text != null,
            b -> b.constraintOnTarget(
                req -> false, "input", "input",
                "Cannot provide both audio and text"))
        .constraintOnCondition(
            (req, group) -> req.audio == null && req.text == null,
            b -> b.constraintOnTarget(
                req -> false, "input", "input",
                "Must provide either audio or text"))
        .build();
    
    /**
     * Validate this request using Yavi
     * 
     * @return Constraint violations (empty if valid)
     */
    public ConstraintViolations validate() {
        return VALIDATOR.validate(this);
    }
    
    /**
     * Check if this request is valid
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return validate().isValid();
    }
}
