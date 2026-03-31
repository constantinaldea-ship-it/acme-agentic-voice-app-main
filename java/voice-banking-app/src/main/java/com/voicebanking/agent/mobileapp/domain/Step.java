package com.voicebanking.agent.mobileapp.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * A single step in a feature guide or navigation path.
 * 
 * Steps are designed to be voice-friendly with clear, actionable instructions.
 * Each step is kept under 15 words for easy voice comprehension.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public record Step(
        int stepNumber,
        String instruction,
        String screenHint,
        String expectedResult
) {
    /**
     * Create a simple step without screen hint or expected result.
     */
    public static Step simple(int stepNumber, String instruction) {
        return new Step(stepNumber, instruction, null, null);
    }

    /**
     * Create a step with screen hint.
     */
    public static Step withHint(int stepNumber, String instruction, String screenHint) {
        return new Step(stepNumber, instruction, screenHint, null);
    }

    /**
     * Format step for voice output.
     * Example: "Step 1: Tap the menu icon in the top left corner"
     */
    public String toVoiceFormat() {
        return String.format("Step %d: %s", stepNumber, instruction);
    }

    /**
     * Convert to Map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("stepNumber", stepNumber);
        map.put("instruction", instruction);
        if (screenHint != null) {
            map.put("screenHint", screenHint);
        }
        if (expectedResult != null) {
            map.put("expectedResult", expectedResult);
        }
        return map;
    }
}
