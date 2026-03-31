package com.voicebanking.adapter.stt;

/**
 * Transcript Response
 * 
 * <p>Output from STT provider after transcribing audio.</p>
 * 
 * @param text Transcribed text
 * @param confidence Confidence score (0.0-1.0), null if not available
 * @param language Language code (e.g., "en-US"), null if not available
 * @param durationMs Duration in milliseconds
 */
public record TranscriptResponse(
    String text,
    Double confidence,
    String language,
    long durationMs
) {
    /**
     * Create a transcript response with defaults
     */
    public static TranscriptResponse of(String text, long durationMs) {
        return new TranscriptResponse(text, null, null, durationMs);
    }
    
    /**
     * Create a transcript response with confidence
     */
    public static TranscriptResponse of(String text, double confidence, long durationMs) {
        return new TranscriptResponse(text, confidence, null, durationMs);
    }
}
